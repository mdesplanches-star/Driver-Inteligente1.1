package br.com.nexo.driver.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import br.com.nexo.driver.capture.OfferCaptureService
import br.com.nexo.driver.destination.DriverDestination
import br.com.nexo.driver.destination.SharedPreferencesDriverDestinationStore
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.profile.DriverProfile
import br.com.nexo.driver.profile.SharedPreferencesProfileStore
import br.com.nexo.driver.overlay.preferences.SharedPreferencesOverlayPreferenceStore
import br.com.nexo.driver.offline.SharedPreferencesOfflineMapPackageStore
import br.com.nexo.driver.permission.CaptureSessionId
import br.com.nexo.driver.permission.PermissionGrant
import br.com.nexo.driver.permission.PermissionReadinessEvaluator
import br.com.nexo.driver.permission.PermissionState
import br.com.nexo.driver.permission.PermissionStateReducer
import br.com.nexo.driver.ui.filters.FiltersScreen
import br.com.nexo.driver.ui.filters.FiltersScreenState
import br.com.nexo.driver.ui.filters.FilterRuleEditorSheet
import br.com.nexo.driver.ui.filters.FilterRuleId
import br.com.nexo.driver.ui.filters.id
import br.com.nexo.driver.ui.destination.HomeDestinationScreen
import br.com.nexo.driver.ui.home.HomeScreen
import br.com.nexo.driver.ui.home.HomeScreenState
import br.com.nexo.driver.ui.permission.PermissionOnboardingActions
import br.com.nexo.driver.ui.permission.PermissionOnboardingSheet
import br.com.nexo.driver.ui.settings.AppFontScale
import br.com.nexo.driver.ui.settings.SettingsScreen
import br.com.nexo.driver.ui.settings.SettingsScreenState
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import br.com.nexo.driver.ui.theme.DriverThemeMode
import java.util.UUID

private enum class AppDestination(
    val label: String,
    val showInBottomBar: Boolean = true,
) {
    HOME("Início"),
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
    var readerEnabled by remember { mutableStateOf(OfferCaptureService.isActive(context)) }
    var showPermissionOnboarding by remember { mutableStateOf(false) }
    var captureSessionId by remember { mutableStateOf(newCaptureSessionId()) }
    val permissionReducer = remember { PermissionStateReducer() }
    val readinessEvaluator = remember { PermissionReadinessEvaluator() }
    var permissionState by remember { mutableStateOf(initialPermissionState(context)) }
    val readiness = readinessEvaluator.evaluate(permissionState, captureSessionId)

    // The service is the source of truth: projection can be stopped by Android at any time (for
    // example from the system privacy controls), without a tap in this Activity.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action != OfferCaptureService.ACTION_READER_STATE_CHANGED) return
                val active = intent.getBooleanExtra(OfferCaptureService.EXTRA_READER_ACTIVE, false)
                readerEnabled = active
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
                onRuleClick = { ruleId -> editingRuleId = ruleId },
                onAddFilter = {
                    updateActiveProfile { profile ->
                        profile.updated(
                            rules = addNextRule(profile.rules),
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                },
            )

            AppDestination.HOME, AppDestination.SETTINGS -> Scaffold(
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
                        ),
                        onReaderEnabledChanged = { enabled ->
                            if (!enabled) {
                                OfferCaptureService.stop(context)
                                readerEnabled = false
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
                        modifier = Modifier.padding(padding),
                    )

                    AppDestination.SETTINGS -> SettingsScreen(
                        state = SettingsScreenState(
                            themeMode = themeMode,
                            fontScale = appFontScale,
                            overlayPreferences = overlayPreferences,
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
}

@Composable
private fun DriverBottomBar(selected: AppDestination, onSelected: (AppDestination) -> Unit) {
    NavigationBar {
        AppDestination.entries.filter { it.showInBottomBar }.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = { Text(destinationIcon(destination)) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun destinationIcon(destination: AppDestination): String = when (destination) {
    AppDestination.HOME -> "⌂"
    AppDestination.FILTERS -> "≡"
    AppDestination.SETTINGS -> "⚙"
    AppDestination.HOME_DESTINATION -> "⌂"
}

private fun DriverDestination.displayName(): String = label ?: "Casa"

private fun DriverDestination.displayDetails(): String =
    "${"%.5f".format(java.util.Locale.US, coordinate.latitude)}, " +
        "${"%.5f".format(java.util.Locale.US, coordinate.longitude)} · raio ${arrivalRadiusMeters.toInt()} m"

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
    FilterRule(Metric.IS_TOWARD_DESTINATION, Comparator.IS_TRUE, enabled = false),
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
