package br.com.nexo.driver.analysis

import android.content.Context
import br.com.nexo.driver.accessibility.AnalysisSource
import br.com.nexo.driver.destination.SharedPreferencesDriverDestinationStore
import br.com.nexo.driver.destination.GeocoderDestinationResolver
import br.com.nexo.driver.destination.GeocodedAddressCache
import br.com.nexo.driver.destination.OfferDestinationGeocoder
import br.com.nexo.driver.destination.offline.DestinationOfferEnricher
import br.com.nexo.driver.destination.offline.OfflineAddressPackageTsvCodec
import br.com.nexo.driver.destination.offline.OfflineAddressResolver
import br.com.nexo.driver.evaluation.EvaluationResult
import br.com.nexo.driver.evaluation.OfferEvaluator
import br.com.nexo.driver.journey.AutoDecisionEngine
import br.com.nexo.driver.journey.AutoDecisionSettingsStore
import br.com.nexo.driver.journey.DailyCostSettingsStore
import br.com.nexo.driver.journey.DailyDriverSummaryCalculator
import br.com.nexo.driver.journey.DriverGoalProgressCalculator
import br.com.nexo.driver.journey.DriverGoalSettingsStore
import br.com.nexo.driver.journey.SharedPreferencesRideHistoryStore
import br.com.nexo.driver.journey.toRideHistoryEntry
import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferField
import br.com.nexo.driver.offline.SharedPreferencesOfflineMapPackageStore
import br.com.nexo.driver.overlay.OfferOverlayPresenter
import br.com.nexo.driver.overlay.OfferOverlayUiModel
import br.com.nexo.driver.overlay.preferences.SharedPreferencesOverlayPreferenceStore
import br.com.nexo.driver.profile.SharedPreferencesProfileStore
import br.com.nexo.driver.speech.OfferDecisionSpeaker
import br.com.nexo.driver.speech.SharedPreferencesSpeechSettingsStore
import br.com.nexo.driver.ui.theme.DriverThemeMode
import java.util.concurrent.Executors

/**
 * Shared final stage for Accessibility and OCR readers. It keeps analysis behaviour identical
 * once either source has produced a parsed offer.
 */
class OfferAnalysisProcessor(
    private val context: Context,
    private val evaluator: OfferEvaluator = OfferEvaluator(),
    private val presenter: OfferOverlayPresenter = OfferOverlayPresenter(evaluator),
    private val speaker: OfferDecisionSpeaker? = null,
) {
    private val appContext = context.applicationContext
    private val offlinePackageStore by lazy { SharedPreferencesOfflineMapPackageStore.create(appContext) }
    private val offlineLoader = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "driver-offline-address-loader").apply { isDaemon = true }
    }
    private val offlineResolverLock = Any()
    @Volatile private var cachedOfflineKey: String? = null
    @Volatile private var cachedOfflineResolver: OfflineAddressResolver? = null
    private var loadingOfflineKey: String? = null
    private val offerDestinationGeocoder by lazy {
        OfferDestinationGeocoder(GeocoderDestinationResolver(appContext, cacheEnabled = false))
    }

    init {
        val migration = appContext.getSharedPreferences(PRIVACY_MIGRATION_PREFERENCES, Context.MODE_PRIVATE)
        if (!migration.getBoolean(KEY_OLD_GEOCODE_CACHE_CLEARED, false)) {
            GeocodedAddressCache.create(appContext).clear()
            appContext.getSharedPreferences(LEGACY_HISTORY_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit()
            migration.edit().putBoolean(KEY_OLD_GEOCODE_CACHE_CLEARED, true).commit()
        }
        refreshOfflineResolverAsync()
    }

    fun analyze(
        offer: NormalizedOffer,
        source: AnalysisSource,
        allowSideEffects: Boolean = true,
    ): OfferAnalysisResult? {
        val enrichedOffer = currentDestinationOfferEnricher()?.enrich(offer) ?: offer.withUnknownHomeMatch()
        val profile = SharedPreferencesProfileStore.create(appContext).load().activeProfile
        val rules = profile?.takeIf { it.isEnabled }?.rules.orEmpty()
        val evaluation = evaluator.evaluate(enrichedOffer, rules)
        val derived = evaluator.derive(enrichedOffer)
        val overlayPreferences = SharedPreferencesOverlayPreferenceStore.create(appContext).load()
        val overlay = presenter.present(enrichedOffer, evaluation, overlayPreferences.fields)
        val settings = SharedPreferencesSpeechSettingsStore.create(appContext).load()

        if (allowSideEffects) {
            OfferSessionMetricsRepository.record(enrichedOffer)
            recordHistoryIfEnabled(enrichedOffer, evaluation, derived.totalDistance.value?.meters, derived.totalDuration.value?.seconds)
        }
        if (allowSideEffects && settings.speakDecision) {
            speaker?.speak(overlay)
        }

        return OfferAnalysisResult(
            offer = enrichedOffer,
            overlay = overlay,
            appearance = currentOverlayAppearance(),
        )
    }

    /**
     * Resolves an uncached textual drop-off after the first overlay is already visible. The
     * returned analysis deliberately disables speech/session counters; the caller also verifies that the
     * same offer is still active before replacing the card.
     */
    fun analyzeDestinationUpdateAsync(
        offer: NormalizedOffer,
        source: AnalysisSource,
        callback: (OfferAnalysisResult) -> Unit,
    ) {
        if (offer.trip.location.value?.coordinate?.isValid == true) return
        refreshOfflineResolverAsync {
            val offlineResult = analyze(offer, source, allowSideEffects = false)
            if (offlineResult?.offer?.endsNearHome?.value != null) {
                callback(offlineResult)
                return@refreshOfflineResolverAsync
            }
            offerDestinationGeocoder.resolveAsync(offer) { enriched ->
                if (enriched.trip.location.value?.coordinate?.isValid != true) return@resolveAsync
                if (enriched.trip.location.value?.coordinate == offer.trip.location.value?.coordinate) return@resolveAsync
                analyze(enriched, source, allowSideEffects = false)?.let(callback)
            }
        }
    }

    /**
     * Fast path used for every offer. It only reads small preferences and an already-decoded,
     * in-memory resolver. Selecting or replacing a TSV schedules decoding away from the capture
     * thread, so disk I/O can never delay the first overlay.
     */
    private fun currentDestinationOfferEnricher(): DestinationOfferEnricher? = runCatching {
        val destination = SharedPreferencesDriverDestinationStore.create(appContext).load() ?: return null
        val selectedPackage = offlinePackageStore.load()
        val key = selectedPackage?.cacheKey()
        val resolver = cachedOfflineResolver.takeIf { key != null && cachedOfflineKey == key }
        if (key != cachedOfflineKey) refreshOfflineResolverAsync()
        DestinationOfferEnricher(resolver, destination)
    }.getOrNull()

    private fun refreshOfflineResolverAsync(onReady: (() -> Unit)? = null) {
        val selectedPackage = offlinePackageStore.load()
        val key = selectedPackage?.cacheKey()
        synchronized(offlineResolverLock) {
            if (key == cachedOfflineKey) {
                onReady?.invoke()
                return
            }
            if (loadingOfflineKey == key) {
                if (onReady != null) offlineLoader.execute {
                    while (synchronized(offlineResolverLock) { loadingOfflineKey == key }) Thread.yield()
                    onReady()
                }
                return
            }
            loadingOfflineKey = key
        }
        offlineLoader.execute {
            val resolver = selectedPackage?.let { mapPackage ->
                runCatching {
                    appContext.contentResolver.openInputStream(android.net.Uri.parse(mapPackage.contentUri))
                        ?.use(::readBoundedAddressPackage)
                        ?.let(OfflineAddressPackageTsvCodec::decode)
                        ?.let(OfflineAddressResolver::create)
                }.getOrNull()
            }
            synchronized(offlineResolverLock) {
                if (offlinePackageStore.load()?.cacheKey() == key) {
                    cachedOfflineKey = key
                    cachedOfflineResolver = resolver
                }
                if (loadingOfflineKey == key) loadingOfflineKey = null
            }
            onReady?.invoke()
        }
    }

    private fun br.com.nexo.driver.offline.OfflineMapPackage.cacheKey(): String =
        "$contentUri|$importedAtEpochMs|${sizeBytes ?: -1L}"

    private fun readBoundedAddressPackage(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_ADDRESS_PACKAGE_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_ADDRESS_PACKAGE_BYTES) {
                throw IllegalArgumentException("Offline address package is too large.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun NormalizedOffer.withUnknownHomeMatch(): NormalizedOffer {
        val homeMatch = Confidence<Boolean>(value = null, score = 0f, source = FieldSource.DERIVED)
        return copy(
            endsNearHome = homeMatch,
            fieldConfidence = fieldConfidence + (OfferField.ENDS_NEAR_HOME to homeMatch.score),
        )
    }

    private fun recordHistoryIfEnabled(
        offer: NormalizedOffer,
        evaluation: EvaluationResult,
        totalDistanceMeters: Long?,
        totalDurationSeconds: Long?,
    ) {
        val historyStore = SharedPreferencesRideHistoryStore.create(appContext)
        if (!historyStore.load().enabled) return
        val settings = DailyCostSettingsStore.create(appContext).load()
        val goalSettings = DriverGoalSettingsStore.create(appContext).load()
        val autoDecisionSettings = AutoDecisionSettingsStore.create(appContext).load()
        val estimatedRealProfit = offer.payout.value?.cents?.let { gross ->
            DailyDriverSummaryCalculator().realProfitCents(
                grossCents = gross,
                distanceMeters = totalDistanceMeters?.toDouble() ?: 0.0,
                settings = settings,
            )
        }
        val summary = DailyDriverSummaryCalculator().summarize(
            distanceMeters = totalDistanceMeters?.toDouble() ?: 0.0,
            clock = br.com.nexo.driver.journey.RideSessionClockState(),
            history = historyStore.load().entries,
            settings = settings,
            nowEpochMs = System.currentTimeMillis(),
        )
        val goalProgress = DriverGoalProgressCalculator().calculate(summary, goalSettings)
        val autoDecision = AutoDecisionEngine().decide(
            settings = autoDecisionSettings,
            evaluation = evaluation,
            estimatedRealProfitCents = estimatedRealProfit,
            goalProgress = goalProgress,
        )
        historyStore.record(
            offer.toRideHistoryEntry(
                decision = evaluation.decision,
                totalDistanceMeters = totalDistanceMeters,
                totalDurationSeconds = totalDurationSeconds,
                estimatedRealProfitCents = estimatedRealProfit,
            ).copy(
                autoDecision = autoDecision.action,
                autoDecisionMode = autoDecision.mode,
            ),
        )
    }

    private fun currentOverlayAppearance(): OverlayAppearance {
        val preferences = appContext.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
        val themeMode = preferences.getString(KEY_THEME_MODE, null)
            ?.let { value -> DriverThemeMode.entries.firstOrNull { it.name == value } }
            ?: DriverThemeMode.SYSTEM
        val fontScale = when (preferences.getString(KEY_FONT_SCALE, null)) {
            "SMALL" -> 0.88f
            "LARGE" -> 1.16f
            else -> 1f
        }
        return OverlayAppearance(themeMode, fontScale)
    }

    private companion object {
        private const val APP_SETTINGS_PREFERENCES = "driver_inteligente_app_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val DEFAULT_ADDRESS_PACKAGE_BUFFER_BYTES = 16 * 1024
        private const val MAX_ADDRESS_PACKAGE_BYTES = 16 * 1024 * 1024
        private const val PRIVACY_MIGRATION_PREFERENCES = "driver_privacy_migrations"
        private const val KEY_OLD_GEOCODE_CACHE_CLEARED = "old_offer_geocode_cache_cleared_v1"
        private const val LEGACY_HISTORY_PREFERENCES = "driver_inteligente_offer_history"
    }
}

data class OfferAnalysisResult(
    val offer: NormalizedOffer,
    val overlay: OfferOverlayUiModel,
    val appearance: OverlayAppearance,
)

data class OverlayAppearance(
    val themeMode: DriverThemeMode,
    val fontScale: Float,
)
