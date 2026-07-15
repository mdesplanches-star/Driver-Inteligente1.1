package br.com.nexo.driver.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.overlay.preferences.OverlayPreferences
import br.com.nexo.driver.overlay.preferences.OverlaySlot
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import br.com.nexo.driver.ui.theme.DriverThemeMode

/**
 * Appearance controls for the driver's app. The host owns persistence and applies the selected
 * theme/font scale at app level through the callbacks, avoiding a split visual state.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    state: SettingsScreenState,
    modifier: Modifier = Modifier,
    onThemeModeChanged: (DriverThemeMode) -> Unit,
    onFontScaleChanged: (AppFontScale) -> Unit,
    onOverlayPreferencesChanged: (OverlayPreferences) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Ajustes", fontWeight = FontWeight.SemiBold) })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Aparência",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Escolha como o Driver Inteligente aparece no seu celular.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PreferenceCard(title = "Tema", description = "As cores de aceite, análise e recusa acompanham o tema.") {
                ChoiceGroup(
                    options = DriverThemeMode.entries,
                    selected = state.themeMode,
                    label = DriverThemeMode::displayName,
                    onSelected = onThemeModeChanged,
                )
            }

            PreferenceCard(title = "Tamanho da fonte", description = "Aumente a leitura sem alterar os filtros.") {
                ChoiceGroup(
                    options = AppFontScale.entries,
                    selected = state.fontScale,
                    label = AppFontScale::label,
                    onSelected = onFontScaleChanged,
                )
            }

            FontPreview(fontScale = state.fontScale)

            Text(
                text = "Campos do overlay",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "O pagamento fica somente no cabeçalho. Escolha os quatro indicadores de apoio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OverlayFieldPreferences(
                preferences = state.overlayPreferences,
                onPreferencesChanged = onOverlayPreferencesChanged,
            )
        }
    }
}

@Composable
private fun OverlayFieldPreferences(
    preferences: OverlayPreferences,
    onPreferencesChanged: (OverlayPreferences) -> Unit,
) {
    OverlaySlot.entries.forEach { slot ->
        val selected = preferences[slot]
        val availableFields = preferences.availableFieldsFor(slot)
        PreferenceCard(
            title = slot.displayName(),
            description = "Campo exibido nesta posição do card.",
        ) {
            ChoiceGroup(
                options = availableFields,
                selected = selected,
                label = OverlayMetricField::label,
                onSelected = { field ->
                    onPreferencesChanged(preferences.withField(slot, field))
                },
            )
        }
    }
}

@Composable
private fun PreferenceCard(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun <T> ChoiceGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .selectable(
                        selected = option == selected,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun FontPreview(fontScale: AppFontScale) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Prévia",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "R$ 2,18/km",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * fontScale.multiplier,
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Valor por quilômetro da corrida",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale.multiplier,
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenLightPreview() {
    DriverInteligenteTheme(mode = DriverThemeMode.LIGHT) {
        SettingsScreen(
            state = SettingsScreenState(),
            onThemeModeChanged = {},
            onFontScaleChanged = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenDarkPreview() {
    DriverInteligenteTheme(mode = DriverThemeMode.DARK) {
        SettingsScreen(
            state = SettingsScreenState(
                themeMode = DriverThemeMode.DARK,
                fontScale = AppFontScale.LARGE,
            ),
            onThemeModeChanged = {},
            onFontScaleChanged = {},
        )
    }
}
