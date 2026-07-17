package br.com.nexo.driver.ui.settings

import br.com.nexo.driver.ui.theme.DriverThemeMode
import br.com.nexo.driver.overlay.preferences.OverlayPreferences
import br.com.nexo.driver.overlay.preferences.OverlaySlot
import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.overlay.OverlayPosition
import br.com.nexo.driver.journey.DailyDriverCostSettings
import br.com.nexo.driver.journey.RideHistoryEntry

/**
 * Preferences exposed by the first version of the settings screen.
 *
 * Persistence and app-wide theme application stay with the screen owner so this UI remains
 * reusable from the main application and previews.
 */
data class SettingsScreenState(
    val themeMode: DriverThemeMode = DriverThemeMode.SYSTEM,
    val fontScale: AppFontScale = AppFontScale.DEFAULT,
    val overlayPreferences: OverlayPreferences = OverlayPreferences.DEFAULT,
    val accessibilityServiceEnabled: Boolean = false,
    val speakDecision: Boolean = true,
    val galleryTestStatus: String? = null,
    val overlayPosition: OverlayPosition = OverlayPosition.BOTTOM,
    val rideHistoryEnabled: Boolean = false,
    val rideHistoryCount: Int = 0,
    val recentRideHistory: List<RideHistoryEntry> = emptyList(),
    val costSettings: DailyDriverCostSettings = DailyDriverCostSettings(),
)

enum class AppFontScale(
    val label: String,
    val multiplier: Float,
) {
    SMALL(label = "Pequena", multiplier = 0.88f),
    DEFAULT(label = "Padrão", multiplier = 1f),
    LARGE(label = "Grande", multiplier = 1.16f),
}

fun DriverThemeMode.displayName(): String = when (this) {
    DriverThemeMode.LIGHT -> "Claro"
    DriverThemeMode.DARK -> "Escuro"
    DriverThemeMode.SYSTEM -> "Sistema"
}

fun OverlaySlot.displayName(): String = when (this) {
    OverlaySlot.TOP_START -> "Superior esquerdo"
    OverlaySlot.TOP_END -> "Superior direito"
    OverlaySlot.BOTTOM_START -> "Inferior esquerdo"
    OverlaySlot.BOTTOM_END -> "Inferior direito"
}

/**
 * Fields that can safely replace [slot]. Existing values in the remaining cells are hidden,
 * preserving the overlay's four-distinct-fields contract before a callback is emitted.
 */
fun OverlayPreferences.availableFieldsFor(slot: OverlaySlot): List<OverlayMetricField> {
    val current = this[slot]
    return OverlayMetricField.entries.filter { field -> field == current || field !in fields }
}
