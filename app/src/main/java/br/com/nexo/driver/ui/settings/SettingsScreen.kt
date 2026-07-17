package br.com.nexo.driver.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import br.com.nexo.driver.journey.DailyDriverCostSettings
import br.com.nexo.driver.journey.RideHistoryDecision
import br.com.nexo.driver.journey.RideHistoryEntry
import br.com.nexo.driver.journey.RideHistoryStatus
import br.com.nexo.driver.journey.formatBrlCompact
import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.overlay.preferences.OverlayPreferences
import br.com.nexo.driver.overlay.preferences.OverlaySlot
import br.com.nexo.driver.overlay.OverlayPosition
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
    onOverlayPositionChanged: (OverlayPosition) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onSpeakDecisionChanged: (Boolean) -> Unit = {},
    onTestGalleryImage: () -> Unit = {},
    onRideHistoryEnabledChanged: (Boolean) -> Unit = {},
    onClearRideHistory: () -> Unit = {},
    onRideStatusChanged: (String, RideHistoryStatus) -> Unit = { _, _ -> },
    onCostSettingsChanged: (DailyDriverCostSettings) -> Unit = {},
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

            PreferenceCard(title = "Posição do overlay", description = "Define onde o card aparece sobre o app de corrida.") {
                ChoiceGroup(
                    options = OverlayPosition.entries,
                    selected = state.overlayPosition,
                    label = OverlayPosition::label,
                    onSelected = onOverlayPositionChanged,
                )
            }

            FontPreview()

            Text(
                text = "Leitura e voz",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "A acessibilidade lê os cards quando o app da corrida expõe texto. O OCR por tela continua como fallback.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PreferenceCard(
                title = "Serviço de acessibilidade",
                description = if (state.accessibilityServiceEnabled) {
                    "Ativo: leitura principal por acessibilidade habilitada."
                } else {
                    "Inativo: toque para abrir as configurações do Android e ativar manualmente."
                },
            ) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenAccessibilitySettings) {
                    Text(if (state.accessibilityServiceEnabled) "Abrir acessibilidade" else "Ativar acessibilidade")
                }
            }
            PreferenceCard(
                title = "Testar imagem da galeria",
                description = "Escolha uma captura da Uber ou 99. A imagem passa pelo mesmo OCR, filtros e overlay, sem ser salva pelo app.",
            ) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onTestGalleryImage) {
                    Text("Selecionar captura")
                }
                state.galleryTestStatus?.let { status ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PreferenceCard(
                title = "Falar decisão da corrida",
                description = "Fala uma vez por oferta nova: aceitar, analisar ou recusar, junto com o valor.",
            ) {
                ToggleRow(
                    label = if (state.speakDecision) "Fala ligada" else "Fala desligada",
                    checked = state.speakDecision,
                    onCheckedChange = onSpeakDecisionChanged,
                )
            }

            Text(
                text = "Jornada e histórico",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            PreferenceCard(
                title = "Salvar histórico local de corridas",
                description = "Guarda somente resumo sanitizado da oferta, com endereços quando disponíveis. OCR bruto e imagens nunca são salvos.",
            ) {
                ToggleRow(
                    label = if (state.rideHistoryEnabled) "Histórico ligado (${state.rideHistoryCount})" else "Histórico desligado",
                    checked = state.rideHistoryEnabled,
                    onCheckedChange = onRideHistoryEnabledChanged,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onClearRideHistory) {
                    Text("Limpar histórico")
                }
            }
            PreferenceCard(
                title = "Custos do dia",
                description = "Usados para estimar lucro real: bruto menos combustível e custos manuais.",
            ) {
                CostSettingsEditor(state.costSettings, onCostSettingsChanged)
            }

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
private fun RideHistoryPreview(
    entries: List<RideHistoryEntry>,
    onRideStatusChanged: (String, RideHistoryStatus) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.take(5).forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column {
                    Text(entry.decision.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(entry.pickupAddress, entry.dropoffAddress).joinToString(" → ").ifBlank { "Endereço não identificado" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    entry.grossCents?.formatBrlCompact() ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                when (entry.status) {
                    RideHistoryStatus.ACCEPTED -> OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onRideStatusChanged(entry.id, RideHistoryStatus.IN_RIDE) },
                    ) { Text("Marcar em corrida") }
                    RideHistoryStatus.IN_RIDE -> Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onRideStatusChanged(entry.id, RideHistoryStatus.COMPLETED) },
                    ) { Text("Concluir corrida") }
                    else -> Unit
                }
            }
        }
    }
}

private fun RideHistoryDecision.label(): String = when (this) {
    RideHistoryDecision.ACCEPT -> "Aceita"
    RideHistoryDecision.ANALYZE -> "Analisada"
    RideHistoryDecision.REJECT -> "Recusada"
}

@Composable
private fun CostSettingsEditor(
    settings: DailyDriverCostSettings,
    onChanged: (DailyDriverCostSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DecimalField(
            label = "Consumo médio (km/l)",
            value = settings.fuelEfficiencyKmPerLiter,
            onValue = { onChanged(settings.copy(fuelEfficiencyKmPerLiter = it.coerceAtLeast(0.1))) },
        )
        MoneyField("Valor do combustível (R$/l)", settings.fuelPriceCentsPerLiter) {
            onChanged(settings.copy(fuelPriceCentsPerLiter = it))
        }
        MoneyField("Manutenção", settings.maintenanceCents) {
            onChanged(settings.copy(maintenanceCents = it))
        }
        MoneyField("Pneus", settings.tireCents) {
            onChanged(settings.copy(tireCents = it))
        }
        MoneyField("Lavagem", settings.washCents) {
            onChanged(settings.copy(washCents = it))
        }
        MoneyField("Taxas/plataforma", settings.platformFeeCents) {
            onChanged(settings.copy(platformFeeCents = it))
        }
        MoneyField("Outros", settings.otherCents) {
            onChanged(settings.copy(otherCents = it))
        }
    }
}

@Composable
private fun DecimalField(label: String, value: Double, onValue: (Double) -> Unit) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = "%.1f".format(java.util.Locale.forLanguageTag("pt-BR"), value),
        onValueChange = { raw -> raw.parseDecimal()?.let(onValue) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun MoneyField(label: String, cents: Long, onValue: (Long) -> Unit) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = "%.2f".format(java.util.Locale.forLanguageTag("pt-BR"), cents / 100.0),
        onValueChange = { raw -> raw.parseDecimal()?.let { onValue((it * 100).toLong().coerceAtLeast(0)) } },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private fun String.parseDecimal(): Double? =
    replace(',', '.').filter { it.isDigit() || it == '.' }.toDoubleOrNull()

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            modifier = Modifier.padding(16.dp),
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
                    .heightIn(min = 48.dp)
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
private fun FontPreview() {
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Valor por quilômetro da corrida",
                style = MaterialTheme.typography.bodyMedium,
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
