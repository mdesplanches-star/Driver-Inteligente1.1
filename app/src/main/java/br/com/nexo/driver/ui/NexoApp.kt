package br.com.nexo.driver.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.text.TextUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.nexo.driver.capture.OfferCaptureService
import br.com.nexo.driver.analysis.OfferSessionMetricsRepository
import br.com.nexo.driver.destination.DriverDestination
import br.com.nexo.driver.destination.SharedPreferencesDriverDestinationStore
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.journey.AutoDecisionMode
import br.com.nexo.driver.journey.AutoDecisionSettingsStore
import br.com.nexo.driver.journey.DailyCostSettingsStore
import br.com.nexo.driver.journey.DailyDriverSummaryCalculator
import br.com.nexo.driver.journey.RideSessionClockStore
import br.com.nexo.driver.journey.RideHistoryStatus
import br.com.nexo.driver.journey.SharedPreferencesRideHistoryStore
import br.com.nexo.driver.journey.formatBrlCompact
import br.com.nexo.driver.journey.formatClockDuration
import br.com.nexo.driver.profile.DriverProfile
import br.com.nexo.driver.profile.SharedPreferencesProfileStore
import br.com.nexo.driver.overlay.preferences.SharedPreferencesOverlayPreferenceStore
import br.com.nexo.driver.overlay.preferences.SharedPreferencesOverlayPositionStore
import br.com.nexo.driver.offline.SharedPreferencesOfflineMapPackageStore
import br.com.nexo.driver.permission.CaptureSessionId
import br.com.nexo.driver.permission.PermissionGrant
import br.com.nexo.driver.permission.PermissionReadinessEvaluator
import br.com.nexo.driver.permission.PermissionState
import br.com.nexo.driver.permission.PermissionStateReducer
import br.com.nexo.driver.ui.filters.FiltersScreen
import br.com.nexo.driver.ui.filters.FiltersScreenState
import br.com.nexo.driver.ui.filters.FilterRuleEditorSheet
import br.com.nexo.driver.ui.filters.FilterPickerSheet
import br.com.nexo.driver.ui.filters.FilterRuleId
import br.com.nexo.driver.ui.filters.FilterProfilePresentation
import br.com.nexo.driver.ui.filters.id
import br.com.nexo.driver.ui.destination.HomeDestinationScreen
import br.com.nexo.driver.ui.history.HistoryScreen
import br.com.nexo.driver.ui.history.HistoryScreenState
import br.com.nexo.driver.ui.home.HomeScreen
import br.com.nexo.driver.ui.home.HomeScreenState
import br.com.nexo.driver.ui.permission.PermissionOnboardingActions
import br.com.nexo.driver.ui.permission.PermissionOnboardingSheet
import br.com.nexo.driver.ui.settings.AppFontScale
import br.com.nexo.driver.ui.settings.SettingsScreen
import br.com.nexo.driver.ui.settings.SettingsScreenState
import br.com.nexo.driver.speech.SharedPreferencesSpeechSettingsStore
import br.com.nexo.driver.speech.SpeechSettings
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import br.com.nexo.driver.ui.theme.DriverThemeMode
import br.com.nexo.driver.accessibility.DriverAccessibilityService
import br.com.nexo.driver.gallery.GalleryOfferTester
import br.com.nexo.driver.gallery.message
import br.com.nexo.driver.R
import br.com.nexo.driver.location.CurrentLocationService
import br.com.nexo.driver.location.CurrentLocationState
import br.com.nexo.driver.location.CurrentLocationStateRepository
import kotlinx.coroutines.delay
import java.util.UUID

private enum class AppDestination(
    val label: String,
    val showInBottomBar: Boolean = true,
) {
    HOME("Início"),
    HISTORY("Histórico"),
    FILTERS("Filtros"),
    SETTINGS("Ajustes"),
    HOME_DESTINATION("Destino casa", showInBottomBar = false),
}

@Composable
fun NexoApp() {
    val context = LocalContext.current
    val appPreferences = remember(context) {
        context.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
    }
    val overlayPreferencesStore = remember(context) {
        SharedPreferencesOverlayPreferenceStore.create(context)
    }
    var overlayPreferences by remember(overlayPreferencesStore) {
        mutableStateOf(overlayPreferencesStore.load())
    }
    val overlayPositionStore = remember(context) { SharedPreferencesOverlayPositionStore.create(context) }
    var overlayPosition by remember(overlayPositionStore) { mutableStateOf(overlayPositionStore.load()) }
    val speechSettingsStore = remember(context) { SharedPreferencesSpeechSettingsStore.create(context) }
    var speechSettings by remember(speechSettingsStore) {
        mutableStateOf(speechSettingsStore.load())
    }
    val rideHistoryStore = remember(context) { SharedPreferencesRideHistoryStore.create(context) }
    var rideHistory by remember(rideHistoryStore) { mutableStateOf(rideHistoryStore.load()) }
    val costSettingsStore = remember(context) { DailyCostSettingsStore.create(context) }
    var costSettings by remember(costSettingsStore) { mutableStateOf(costSettingsStore.load()) }
    val clockStore = remember(context) { RideSessionClockStore.create(context) }
    var clockState by remember(clockStore) { mutableStateOf(clockStore.load()) }
    val autoDecisionSettingsStore = remember(context) { AutoDecisionSettingsStore.create(context) }
    var autoDecisionSettings by remember(autoDecisionSettingsStore) {
        mutableStateOf(autoDecisionSettingsStore.load())
    }
    var accessibilityServiceEnabled by remember(context) {
        mutableStateOf(context.isDriverAccessibilityServiceEnabled())
    }
    val galleryOfferTester = remember(context) { GalleryOfferTester(context) }
    var galleryTestStatus by remember { mutableStateOf<String?>(null) }
    DisposableEffect(galleryOfferTester) {
        onDispose { galleryOfferTester.close() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityServiceEnabled = context.isDriverAccessibilityServiceEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var themeMode by remember(appPreferences) {
        mutableStateOf(appPreferences.readThemeMode())
    }
    var appFontScale by remember(appPreferences) {
        mutableStateOf(appPreferences.readFontScale())
    }
    val profileStore = remember(context) { SharedPreferencesProfileStore.create(context) }
    val homeDestinationStore = remember(context) { SharedPreferencesDriverDestinationStore.create(context) }
    var homeDestination by remember(homeDestinationStore) { mutableStateOf(homeDestinationStore.load()) }
    val offlineMapPackageStore = remember(context) { SharedPreferencesOfflineMapPackageStore.create(context) }
    var offlineMapPackage by remember(offlineMapPackageStore) {
        mutableStateOf(offlineMapPackageStore.load())
    }
    var profileSnapshot by remember(profileStore) {
        val stored = profileStore.load()
        mutableStateOf(
            stored.takeIf { it.activeProfile != null } ?: profileStore.save(
                DriverProfile.create(
                    name = "Dia a dia",
                    rules = defaultRules(),
                    nowEpochMs = System.currentTimeMillis(),
                ),
            ),
        )
    }
    val activeProfile = requireNotNull(profileSnapshot.activeProfile)
    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var editingRuleId by remember { mutableStateOf<FilterRuleId?>(null) }
    var showFilterPicker by remember { mutableStateOf(false) }
    var readerEnabled by remember { mutableStateOf(OfferCaptureService.isActive(context)) }
    var locationSnapshot by remember { mutableStateOf(CurrentLocationStateRepository.current()) }
    var sessionMetrics by remember { mutableStateOf(OfferSessionMetricsRepository.current()) }
    var showPermissionOnboarding by remember { mutableStateOf(false) }
    var captureSessionId by remember { mutableStateOf(newCaptureSessionId()) }
    val permissionReducer = remember { PermissionStateReducer() }
    val readinessEvaluator = remember { PermissionReadinessEvaluator() }
    var permissionState by remember { mutableStateOf(initialPermissionState(context)) }
    var pendingLocationStart by remember { mutableStateOf(false) }
    val readiness = readinessEvaluator.evaluate(permissionState, captureSessionId)
    var clockTickEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            clockTickEpochMs = System.currentTimeMillis()
        }
    }
    val nowEpochMs = clockTickEpochMs
    val daySummary = DailyDriverSummaryCalculator().summarize(
        distanceMeters = locationSnapshot.sessionDistanceMeters,
        clock = clockState,
        history = rideHistory.entries,
        settings = costSettings,
        nowEpochMs = nowEpochMs,
    )

    DisposableEffect(Unit) {
        val locationSubscription = CurrentLocationStateRepository.subscribe { snapshot -> locationSnapshot = snapshot }
        val offerSubscription = OfferSessionMetricsRepository.subscribe { metrics ->
            sessionMetrics = metrics
            rideHistory = rideHistoryStore.load()
        }
        onDispose {
            locationSubscription.close()
            offerSubscription.close()
        }
    }

    BackHandler(
        enabled = editingRuleId != null || showFilterPicker || showPermissionOnboarding ||
            destination == AppDestination.FILTERS || destination == AppDestination.HOME_DESTINATION ||
            destination == AppDestination.HISTORY || destination == AppDestination.SETTINGS,
    ) {
        when {
            editingRuleId != null -> editingRuleId = null
            showFilterPicker -> showFilterPicker = false
            showPermissionOnboarding -> showPermissionOnboarding = false
            else -> destination = AppDestination.HOME
        }
    }

    // The service is the source of truth: projection can be stopped by Android at any time (for
    // example from the system privacy controls), without a tap in this Activity.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action != OfferCaptureService.ACTION_READER_STATE_CHANGED) return
                val active = intent.getBooleanExtra(OfferCaptureService.EXTRA_READER_ACTIVE, false)
                readerEnabled = active
                clockState = clockStore.setReaderActive(active)
                if (!active) {
                    permissionState = permissionReducer.clearMediaProjection(permissionState)
                    captureSessionId = newCaptureSessionId()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(OfferCaptureService.ACTION_READER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Covers Activity recreation while a valid foreground capture continues.
        readerEnabled = OfferCaptureService.isActive(context)
        clockState = clockStore.setReaderActive(readerEnabled)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionState = permissionReducer.setOverlay(
            permissionState,
            context.overlayPermissionGrant(),
        )
    }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionState = permissionReducer.setNotifications(
            permissionState,
            if (granted) PermissionGrant.GRANTED else PermissionGrant.DENIED,
        )
        if (pendingLocationStart) {
            pendingLocationStart = false
            if (granted) {
                CurrentLocationService.start(context)
            } else {
                CurrentLocationStateRepository.update(CurrentLocationState.PermissionMissing)
            }
        }
    }
    val locationPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingLocationStart = true
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                CurrentLocationService.start(context)
            }
        }
    }
    val mediaProjectionManager = remember(context) {
        context.getSystemService(MediaProjectionManager::class.java)
    }
    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            permissionState = permissionReducer.grantMediaProjection(permissionState, captureSessionId)
            OfferCaptureService.start(context, result.resultCode, data)
            // Wait for the service to create the virtual display and publish its active state.
            // This avoids showing an enabled switch when Android rejects a one-shot consent.
            readerEnabled = false
            showPermissionOnboarding = false
        } else {
            permissionState = permissionReducer.denyMediaProjection(permissionState)
            readerEnabled = false
        }
    }
    val galleryImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            galleryTestStatus = "Nenhuma imagem selecionada."
        } else {
            galleryTestStatus = "Lendo imagem e calculando oferta…"
            galleryOfferTester.test(uri) { result ->
                galleryTestStatus = result.message()
            }
        }
    }

    fun updateActiveProfile(transform: (DriverProfile) -> DriverProfile) {
        profileSnapshot = profileStore.save(transform(activeProfile))
    }

    DriverInteligenteTheme(mode = themeMode, fontScale = appFontScale.multiplier) {
        when (destination) {
            AppDestination.HOME_DESTINATION -> HomeDestinationScreen(
                currentDestination = homeDestination,
                currentOfflineMapPackage = offlineMapPackage,
                onNavigateBack = { destination = AppDestination.HOME },
                onSave = { selected ->
                    homeDestination = homeDestinationStore.save(selected)
                    destination = AppDestination.HOME
                },
                onDraftChanged = { draft ->
                    homeDestination = homeDestinationStore.save(draft)
                },
                onClear = {
                    homeDestinationStore.clear()
                    homeDestination = null
                    destination = AppDestination.HOME
                },
                onOfflineMapImported = { selected ->
                    offlineMapPackage
                        ?.takeIf { previous -> previous.contentUri != selected.contentUri }
                        ?.let { previous -> context.releaseOfflineMapReadPermission(previous.contentUri) }
                    offlineMapPackage = offlineMapPackageStore.save(selected)
                },
                onOfflineMapRemoved = { removed ->
                    context.releaseOfflineMapReadPermission(removed.contentUri)
                    offlineMapPackageStore.clear()
                    offlineMapPackage = null
                },
            )

            AppDestination.FILTERS -> FiltersScreen(
                state = FiltersScreenState(
                    profileName = activeProfile.name,
                    profiles = profileSnapshot.profiles.map { profile ->
                        FilterProfilePresentation(
                            id = profile.id,
                            name = profile.name,
                            isActive = profile.id == profileSnapshot.activeProfileId,
                        )
                    },
                    activeProfileId = profileSnapshot.activeProfileId,
                    isProfileEnabled = activeProfile.isEnabled,
                    rules = activeProfile.rules,
                ),
                onNavigateBack = {
                    editingRuleId = null
                    destination = AppDestination.HOME
                },
                onProfileEnabledChange = { enabled ->
                    updateActiveProfile { profile ->
                        profile.updated(isEnabled = enabled, updatedAtEpochMs = System.currentTimeMillis())
                    }
                },
                onProfileSelected = { profileId ->
                    profileSnapshot = profileStore.setActiveProfile(profileId)
                },
                onCreateProfile = {
                    val nextIndex = profileSnapshot.profiles.size + 1
                    profileSnapshot = profileStore.save(
                        DriverProfile.create(
                            name = "Perfil $nextIndex",
                            rules = defaultRules(),
                            nowEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    profileSnapshot.activeProfileId?.let { profileSnapshot = profileStore.setActiveProfile(profileSnapshot.profiles.last().id) }
                },
                onDeleteActiveProfile = {
                    val activeId = profileSnapshot.activeProfileId
                    if (activeId != null && profileSnapshot.profiles.size > 1) {
                        val afterDelete = profileStore.delete(activeId)
                        profileSnapshot = afterDelete.activeProfileId
                            ?.let { afterDelete }
                            ?: afterDelete.profiles.firstOrNull()
                                ?.let { profileStore.setActiveProfile(it.id) }
                            ?: afterDelete
                    }
                },
                onRuleEnabledChange = { ruleId, enabled ->
                    updateActiveProfile { profile ->
                        profile.updated(
                            rules = profile.rules.map { rule ->
                                if (rule.id == ruleId) rule.copy(enabled = enabled) else rule
                            },
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                },
                onRuleDelete = { ruleId ->
                    updateActiveProfile { profile ->
                        profile.updated(
                            rules = profile.rules.filterNot { it.id == ruleId },
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                },
                onRuleClick = { ruleId -> editingRuleId = ruleId },
                onAddFilter = {
                    showFilterPicker = true
                },
                bottomBar = {
                    DriverBottomBar(selected = AppDestination.FILTERS, onSelected = { destination = it })
                },
            )

            AppDestination.HOME, AppDestination.HISTORY, AppDestination.SETTINGS -> Scaffold(
                bottomBar = {
                    DriverBottomBar(selected = destination, onSelected = { destination = it })
                },
            ) { padding ->
                when (destination) {
                    AppDestination.HOME -> HomeScreen(
                        state = HomeScreenState(
                            readerEnabled = readerEnabled,
                            activeProfileName = activeProfile.name,
                            activeProfileSummary = activeProfile.rules
                                .filter { it.enabled }
                                .take(2)
                                .joinToString(" · ") { it.metric.shortLabel() },
                            homeDestination = homeDestination?.displayName(),
                            homeDestinationDetails = homeDestination?.displayDetails(),
                            kilometresAnalyzed = locationSnapshot.sessionDistanceMeters / 1_000.0,
                            offersEvaluated = sessionMetrics.offersEvaluated,
                            onlineTime = daySummary.onlineMillis.formatClockDuration(),
                            rideTime = daySummary.rideMillis.formatClockDuration(),
                            grossProfit = daySummary.grossProfitCents.formatBrlCompact(),
                            realProfit = daySummary.realProfitCents.formatBrlCompact(),
                            fuelCost = daySummary.fuelCostCents.formatBrlCompact(),
                            extraCost = daySummary.extraCostCents.formatBrlCompact(),
                            rideClockActive = clockState.rideStartedAtEpochMs != null,
                            autoAcceptEnabled = autoDecisionSettings.autoAcceptEnabled,
                            autoRejectEnabled = autoDecisionSettings.autoRejectEnabled,
                            location = locationSnapshot,
                        ),
                        onReaderEnabledChanged = { enabled ->
                            if (!enabled) {
                                OfferCaptureService.stop(context)
                                readerEnabled = false
                                clockState = clockStore.setReaderActive(false)
                                permissionState = permissionReducer.clearMediaProjection(permissionState)
                                captureSessionId = newCaptureSessionId()
                            } else {
                                captureSessionId = newCaptureSessionId()
                                permissionState = permissionReducer.clearMediaProjection(permissionState)
                                showPermissionOnboarding = true
                            }
                        },
                        onOpenFilters = { destination = AppDestination.FILTERS },
                        onConfigureHome = { destination = AppDestination.HOME_DESTINATION },
                        onRideClockActiveChanged = { active ->
                            clockState = clockStore.setRideActive(active)
                        },
                        onAutoAcceptChanged = { enabled ->
                            val next = autoDecisionSettings.copy(
                                autoAcceptEnabled = enabled,
                                mode = if (enabled || autoDecisionSettings.autoRejectEnabled) {
                                    AutoDecisionMode.ASSISTIVE
                                } else {
                                    AutoDecisionMode.OFF
                                },
                            )
                            autoDecisionSettings = autoDecisionSettingsStore.save(next)
                        },
                        onAutoRejectChanged = { enabled ->
                            val next = autoDecisionSettings.copy(
                                autoRejectEnabled = enabled,
                                mode = if (autoDecisionSettings.autoAcceptEnabled || enabled) {
                                    AutoDecisionMode.ASSISTIVE
                                } else {
                                    AutoDecisionMode.OFF
                                },
                            )
                            autoDecisionSettings = autoDecisionSettingsStore.save(next)
                        },
                        onLocationEnabledChanged = { enabled ->
                            if (!enabled) {
                                CurrentLocationService.stop(context)
                            } else {
                                val fineGranted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ) == PackageManager.PERMISSION_GRANTED
                                val coarseGranted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (fineGranted || coarseGranted) {
                                    if (
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        pendingLocationStart = true
                                        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        CurrentLocationService.start(context)
                                    }
                                } else {
                                    locationPermissionsLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.padding(padding),
                    )

                    AppDestination.HISTORY -> HistoryScreen(
                        state = HistoryScreenState(
                            enabled = rideHistory.enabled,
                            entries = rideHistory.entries,
                        ),
                        onEnabledChanged = { enabled ->
                            rideHistory = rideHistoryStore.setEnabled(enabled)
                        },
                        onClearHistory = {
                            rideHistory = rideHistoryStore.clear()
                        },
                        onRideStatusChanged = { rideId, status ->
                            rideHistory = rideHistoryStore.updateStatus(rideId, status)
                            if (status == RideHistoryStatus.IN_RIDE) {
                                clockState = clockStore.setRideActive(true)
                            } else if (status == RideHistoryStatus.COMPLETED) {
                                clockState = clockStore.setRideActive(false)
                            }
                        },
                        modifier = Modifier.padding(padding),
                    )

                    AppDestination.SETTINGS -> SettingsScreen(
                        state = SettingsScreenState(
                            themeMode = themeMode,
                            fontScale = appFontScale,
                            overlayPreferences = overlayPreferences,
                            accessibilityServiceEnabled = accessibilityServiceEnabled,
                            speakDecision = speechSettings.speakDecision,
                            galleryTestStatus = galleryTestStatus,
                            overlayPosition = overlayPosition,
                            rideHistoryEnabled = rideHistory.enabled,
                            rideHistoryCount = rideHistory.entries.size,
                            recentRideHistory = rideHistory.entries.take(5),
                            costSettings = costSettings,
                        ),
                        onThemeModeChanged = { selected ->
                            themeMode = selected
                            appPreferences.edit().putString(KEY_THEME_MODE, selected.name).apply()
                        },
                        onFontScaleChanged = { selected ->
                            appFontScale = selected
                            appPreferences.edit().putString(KEY_FONT_SCALE, selected.name).apply()
                        },
                        onOverlayPreferencesChanged = { selected ->
                            overlayPreferences = overlayPreferencesStore.save(selected)
                        },
                        onOverlayPositionChanged = { selected ->
                            overlayPosition = overlayPositionStore.save(selected)
                        },
                        onOpenAccessibilitySettings = {
                            accessibilityServiceEnabled = context.isDriverAccessibilityServiceEnabled()
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        onSpeakDecisionChanged = { enabled ->
                            speechSettings = speechSettingsStore.save(speechSettings.copy(speakDecision = enabled))
                        },
                        onRideHistoryEnabledChanged = { enabled ->
                            rideHistory = rideHistoryStore.setEnabled(enabled)
                        },
                        onClearRideHistory = {
                            rideHistory = rideHistoryStore.clear()
                        },
                        onRideStatusChanged = { rideId, status ->
                            rideHistory = rideHistoryStore.updateStatus(rideId, status)
                            if (status == RideHistoryStatus.IN_RIDE) {
                                clockState = clockStore.setRideActive(true)
                            } else if (status == RideHistoryStatus.COMPLETED) {
                                clockState = clockStore.setRideActive(false)
                            }
                        },
                        onCostSettingsChanged = { selected ->
                            costSettings = costSettingsStore.save(selected)
                        },
                        onTestGalleryImage = {
                            if (!Settings.canDrawOverlays(context)) {
                                galleryTestStatus = "Autorize a sobreposição antes do teste para visualizar o card."
                                overlaySettingsLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                                )
                            } else {
                                galleryImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
                        },
                        modifier = Modifier.padding(padding),
                    )
                    AppDestination.FILTERS -> Unit
                    AppDestination.HOME_DESTINATION -> Unit
                }
            }
        }
        if (showPermissionOnboarding) {
            PermissionOnboardingSheet(
                state = permissionState,
                readiness = readiness,
                actions = PermissionOnboardingActions(
                    requestOverlay = {
                        overlaySettingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    requestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            permissionState = permissionReducer.setNotifications(permissionState, PermissionGrant.GRANTED)
                        }
                    },
                    requestCaptureSession = {
                        if (permissionState.overlay != PermissionGrant.GRANTED) {
                            overlaySettingsLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        } else {
                            captureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                        }
                    },
                    dismiss = {
                        showPermissionOnboarding = false
                        if (!readerEnabled) permissionState = permissionReducer.clearMediaProjection(permissionState)
                    },
                ),
            )
        }
        val editingRule = editingRuleId?.let { ruleId ->
            activeProfile.rules.firstOrNull { it.id == ruleId }
        }
        if (editingRule != null) {
            FilterRuleEditorSheet(
                rule = editingRule,
                onSave = { updatedRule ->
                    updateActiveProfile { profile ->
                        val originalId = editingRule.id
                        profile.updated(
                            rules = profile.rules.replaceRule(originalId, updatedRule),
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                    editingRuleId = null
                },
                onDismiss = { editingRuleId = null },
            )
        }
        if (showFilterPicker) {
            FilterPickerSheet(
                existingRules = activeProfile.rules,
                onSelect = { addedRule ->
                    updateActiveProfile { profile ->
                        profile.updated(
                            rules = profile.rules + addedRule,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                    editingRuleId = addedRule.id
                    showFilterPicker = false
                },
                onDismiss = { showFilterPicker = false },
            )
        }
    }
}

private fun newCaptureSessionId() = CaptureSessionId(UUID.randomUUID().toString())

private fun initialPermissionState(context: android.content.Context): PermissionState = PermissionState(
    overlay = context.overlayPermissionGrant(),
    notifications = if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) PermissionGrant.GRANTED else PermissionGrant.NOT_REQUESTED,
)

private fun android.content.Context.overlayPermissionGrant(): PermissionGrant =
    if (Settings.canDrawOverlays(this)) PermissionGrant.GRANTED else PermissionGrant.NOT_REQUESTED

private fun android.content.Context.isDriverAccessibilityServiceEnabled(): Boolean {
    val expected = ComponentName(this, DriverAccessibilityService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    return splitter.any { service -> service.equals(expected, ignoreCase = true) }
}

private fun Metric.shortLabel(): String = when (this) {
    Metric.PAYOUT -> "Pagamento"
    Metric.RATE_PER_KM -> "R$/km"
    Metric.RATE_PER_HOUR -> "R$/h"
    Metric.PICKUP_DISTANCE -> "Dist. retirada"
    Metric.PICKUP_DURATION -> "Tempo retirada"
    Metric.TRIP_DISTANCE -> "Dist. viagem"
    Metric.TRIP_DURATION -> "Tempo viagem"
    Metric.TOTAL_DISTANCE -> "Dist. total"
    Metric.TOTAL_DURATION -> "Tempo total"
    Metric.PASSENGER_RATING -> "Nota"
    Metric.HAS_MULTIPLE_STOPS -> "Paradas"
    Metric.IS_LONG_TRIP -> "Viagem longa"
    Metric.IS_TOWARD_DESTINATION -> "Para casa"
    Metric.ENDS_NEAR_HOME -> "Perto de casa"
}

@Composable
private fun DriverBottomBar(selected: AppDestination, onSelected: (AppDestination) -> Unit) {
    NavigationBar {
        AppDestination.entries.filter { it.showInBottomBar }.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destinationIconRes(destination)),
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun destinationIconRes(destination: AppDestination): Int = when (destination) {
    AppDestination.HOME, AppDestination.HOME_DESTINATION -> R.drawable.ic_navigation_home
    AppDestination.HISTORY -> R.drawable.ic_navigation_history
    AppDestination.FILTERS -> R.drawable.ic_navigation_filters
    AppDestination.SETTINGS -> R.drawable.ic_navigation_settings
}

private fun DriverDestination.displayName(): String = label ?: originalAddress ?: "Casa"

private fun DriverDestination.displayDetails(): String = coordinate?.let {
    "${standardizedAddress ?: originalAddress ?: "Endereço resolvido"} · raio ${arrivalRadiusMeters.toInt()} m"
} ?: "Somente comparação textual · raio ${arrivalRadiusMeters.toInt()} m"

private fun Context.releaseOfflineMapReadPermission(contentUri: String) {
    runCatching {
        contentResolver.releasePersistableUriPermission(
            Uri.parse(contentUri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private fun defaultRules(): List<FilterRule> = listOf(
    FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 800),
    FilterRule(Metric.RATE_PER_HOUR, Comparator.AT_LEAST, target = 4_000),
    FilterRule(Metric.RATE_PER_KM, Comparator.AT_LEAST, target = 175),
    FilterRule(Metric.PICKUP_DURATION, Comparator.AT_MOST, target = 360),
    FilterRule(Metric.PICKUP_DISTANCE, Comparator.AT_MOST, target = 2_500),
    FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 480),
    FilterRule(Metric.HAS_MULTIPLE_STOPS, Comparator.IS_FALSE),
    FilterRule(Metric.ENDS_NEAR_HOME, Comparator.IS_TRUE, enabled = false),
)

private fun addNextRule(rules: List<FilterRule>): List<FilterRule> {
    val candidates = listOf(
        FilterRule(Metric.TRIP_DURATION, Comparator.AT_LEAST, target = 300),
        FilterRule(Metric.TRIP_DURATION, Comparator.AT_MOST, target = 1_800),
        FilterRule(Metric.TOTAL_DISTANCE, Comparator.AT_LEAST, target = 2_000),
        FilterRule(Metric.TOTAL_DISTANCE, Comparator.AT_MOST, target = 12_000),
        FilterRule(Metric.IS_LONG_TRIP, Comparator.IS_FALSE),
    )
    return candidates.firstOrNull { candidate -> rules.none { it.id == candidate.id } }
        ?.let { rules + it }
        ?: rules
}

/**
 * Replaces the rule selected by its original identity. If the comparator was changed to an
 * already configured bound, the edited value wins and the obsolete duplicate is removed.
 */
private fun List<FilterRule>.replaceRule(originalId: FilterRuleId, replacement: FilterRule): List<FilterRule> =
    mapNotNull { rule ->
        when {
            rule.id == originalId -> replacement
            replacement.id != originalId && rule.id == replacement.id -> null
            else -> rule
        }
    }

private fun android.content.SharedPreferences.readThemeMode(): DriverThemeMode =
    getString(KEY_THEME_MODE, null)
        ?.let { saved -> DriverThemeMode.entries.firstOrNull { it.name == saved } }
        ?: DriverThemeMode.SYSTEM

private fun android.content.SharedPreferences.readFontScale(): AppFontScale =
    getString(KEY_FONT_SCALE, null)
        ?.let { saved -> AppFontScale.entries.firstOrNull { it.name == saved } }
        ?: AppFontScale.DEFAULT

private const val APP_SETTINGS_PREFERENCES = "driver_inteligente_app_settings"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_FONT_SCALE = "font_scale"
