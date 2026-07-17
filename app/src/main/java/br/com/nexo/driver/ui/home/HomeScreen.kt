package br.com.nexo.driver.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.location.CurrentLocationServiceSnapshot
import br.com.nexo.driver.location.CurrentLocationServiceStatus

data class HomeScreenState(
    val readerEnabled: Boolean = false,
    val activeProfileName: String = "Dia a dia",
    val activeProfileSummary: String = "R$/km ≥ 1,75 · R$/h ≥ 40",
    val homeDestination: String? = null,
    val homeDestinationDetails: String? = null,
    val kilometresAnalyzed: Double = 0.0,
    val offersEvaluated: Int = 0,
    val onlineTime: String = "0min",
    val rideTime: String = "0min",
    val grossProfit: String = "R$ 0,00",
    val realProfit: String = "R$ 0,00",
    val fuelCost: String = "R$ 0,00",
    val extraCost: String = "R$ 0,00",
    val rideClockActive: Boolean = false,
    val autoAcceptEnabled: Boolean = true,
    val autoRejectEnabled: Boolean = false,
    val location: CurrentLocationServiceSnapshot = CurrentLocationServiceSnapshot(),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeScreenState,
    onReaderEnabledChanged: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onConfigureHome: () -> Unit,
    onLocationEnabledChanged: (Boolean) -> Unit = {},
    onRideClockActiveChanged: (Boolean) -> Unit = {},
    onAutoAcceptChanged: (Boolean) -> Unit = {},
    onAutoRejectChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Driver Inteligente", fontWeight = FontWeight.SemiBold) },
            actions = {
                ReaderIndicator(isActive = state.readerEnabled)
                Spacer(Modifier.width(20.dp))
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoalCockpitCard(state)
            AutoDecisionToggleCard(
                autoAcceptEnabled = state.autoAcceptEnabled,
                autoRejectEnabled = state.autoRejectEnabled,
                onAutoAcceptChanged = onAutoAcceptChanged,
                onAutoRejectChanged = onAutoRejectChanged,
            )
            OperationsGrid(state, onRideClockActiveChanged, onLocationEnabledChanged)
            ReaderCard(state.readerEnabled, onReaderEnabledChanged)
            ProfileCard(state.activeProfileName, state.activeProfileSummary, onOpenFilters)
            HomeDestinationButton(state.homeDestination, onConfigureHome)
            TodaySummary(state)
        }
    }
}

@Composable
private fun GoalCockpitCard(state: HomeScreenState) {
    val progress = estimateGoalProgress(state).coerceIn(0f, 1f)
    NeonCard(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        backgroundTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GoalRing(progress = progress, label = "${(progress * 100).toInt()}%")
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Meta do dia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Lucro real em destaque", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProgressMetric("Lucro real", state.realProfit, progress)
                ProgressMetric("Lucro bruto", state.grossProfit, progress * 0.92f)
                ProgressMetric("Km rodados", "%.1f km".format(java.util.Locale.forLanguageTag("pt-BR"), state.kilometresAnalyzed), progress * 0.84f)
            }
        }
    }
}

@Composable
private fun GoalRing(progress: Float, label: String) {
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(
                Brush.sweepGradient(
                    0f to MaterialTheme.colorScheme.primary,
                    progress to MaterialTheme.colorScheme.primary,
                    progress to MaterialTheme.colorScheme.surfaceVariant,
                    1f to MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            .padding(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProgressMetric(label: String, value: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
private fun AutoDecisionToggleCard(
    autoAcceptEnabled: Boolean,
    autoRejectEnabled: Boolean,
    onAutoAcceptChanged: (Boolean) -> Unit,
    onAutoRejectChanged: (Boolean) -> Unit,
) {
    NeonCard(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.48f),
        backgroundTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ToggleActionButton(
                label = "Auto aceitar",
                enabled = autoAcceptEnabled,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = { onAutoAcceptChanged(!autoAcceptEnabled) },
            )
            ToggleActionButton(
                label = "Auto recusar",
                enabled = autoRejectEnabled,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
                onClick = { onAutoRejectChanged(!autoRejectEnabled) },
            )
        }
    }
}

@Composable
private fun ToggleActionButton(
    label: String,
    enabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = if (enabled) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerLow
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.70f) else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = container),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(if (enabled) "ON" else "OFF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OperationsGrid(
    state: HomeScreenState,
    onRideClockActiveChanged: (Boolean) -> Unit,
    onLocationEnabledChanged: (Boolean) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OperationTile("Tempo online", state.onlineTime, modifier = Modifier.weight(1f))
        OperationTile(
            "Em corrida",
            state.rideTime,
            switchChecked = state.rideClockActive,
            onSwitchChanged = onRideClockActiveChanged,
            modifier = Modifier.weight(1f),
        )
        OperationTile("Combustível", state.fuelCost, modifier = Modifier.weight(1f))
        OperationTile(
            label = "GPS",
            value = gpsStatusLabel(state.location),
            switchChecked = state.location.status != CurrentLocationServiceStatus.IDLE,
            onSwitchChanged = onLocationEnabledChanged,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OperationTile(
    label: String,
    value: String,
    switchChecked: Boolean? = null,
    onSwitchChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (switchChecked != null && onSwitchChanged != null) {
                Switch(checked = switchChecked, onCheckedChange = onSwitchChanged)
            }
        }
    }
}

@Composable
private fun RideClockCard(
    active: Boolean,
    rideTime: String,
    onActiveChanged: (Boolean) -> Unit,
) {
    CompactControlCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Corrida em andamento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (active) "Marcando tempo · $rideTime" else "Toque ao iniciar uma corrida aceita",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = active, onCheckedChange = onActiveChanged)
        }
    }
}

@Composable
private fun CurrentLocationCard(
    snapshot: CurrentLocationServiceSnapshot,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val enabled = snapshot.status in setOf(
        CurrentLocationServiceStatus.ACQUIRING,
        CurrentLocationServiceStatus.ACTIVE,
        CurrentLocationServiceStatus.FIX_REJECTED,
        CurrentLocationServiceStatus.MOVEMENT_REJECTED,
    )
    val description = when (snapshot.status) {
        CurrentLocationServiceStatus.IDLE -> "Desligado. Independente da análise de ofertas."
        CurrentLocationServiceStatus.ACQUIRING -> "Procurando localização…"
        CurrentLocationServiceStatus.ACTIVE -> if (snapshot.isLastKnown) "Última localização conhecida" else "Ativo"
        CurrentLocationServiceStatus.PERMISSION_MISSING -> "Permissão de localização necessária."
        CurrentLocationServiceStatus.PROVIDER_UNAVAILABLE -> "Ative GPS ou localização do celular."
        CurrentLocationServiceStatus.FIX_REJECTED -> "Aguardando localização."
        CurrentLocationServiceStatus.MOVEMENT_REJECTED -> "Movimento descartado; tentando novamente."
    }
    CompactControlCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Localização GPS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (snapshot.status != CurrentLocationServiceStatus.IDLE) {
                    Text(
                        "Sessão: ${"%.1f".format(java.util.Locale.forLanguageTag("pt-BR"), snapshot.sessionDistanceMeters / 1_000.0)} km",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChanged)
        }
    }
}

@Composable
private fun ReaderIndicator(isActive: Boolean) {
    val label = if (isActive) "●" else "○"
    Text(label, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
}

@Composable
private fun ReaderCard(enabled: Boolean, onEnabledChanged: (Boolean) -> Unit) {
    CompactControlCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Captura de ofertas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (enabled) "Monitorando ofertas" else "Pausado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChanged)
        }
    }
}

@Composable
private fun ProfileCard(name: String, summary: String, onOpenFilters: () -> Unit) {
    NeonCard(onClick = onOpenFilters) {
        Text("Perfil ativo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeDestinationButton(destination: String?, onConfigureHome: () -> Unit) {
    Button(modifier = Modifier.fillMaxWidth(), onClick = onConfigureHome) {
        Text(destination?.let { "Destino casa: $it" } ?: "Configurar destino casa")
    }
}

@Composable
private fun TodaySummary(state: HomeScreenState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Hoje", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        NeonCard {
            MetricRow("km rodados", "%.1f".format(java.util.Locale.forLanguageTag("pt-BR"), state.kilometresAnalyzed))
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            MetricRow("tempo online", state.onlineTime)
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            MetricRow("tempo em corrida", state.rideTime)
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            MetricRow("lucro bruto", state.grossProfit)
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            MetricRow("lucro real", state.realProfit)
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            MetricRow("combustível estimado", state.fuelCost)
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            MetricRow("custos extras", state.extraCost)
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            MetricRow("ofertas avaliadas", state.offersEvaluated.toString())
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompactControlCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val cardContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp), content = { content() })
    }
    if (onClick != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            content = { cardContent() },
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            content = { cardContent() },
        )
    }
}

@Composable
private fun NeonCard(
    onClick: (() -> Unit)? = null,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    backgroundTint: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(backgroundTint, Color.Transparent),
                    ),
                )
                .padding(18.dp),
            content = { content() },
        )
    }
    if (onClick != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
            ),
            border = BorderStroke(1.dp, borderColor),
            content = { cardContent() },
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
            ),
            border = BorderStroke(1.dp, borderColor),
            content = { cardContent() },
        )
    }
}

private fun estimateGoalProgress(state: HomeScreenState): Float {
    val real = state.realProfit.extractCurrencyValue()
    val gross = state.grossProfit.extractCurrencyValue()
    val distance = state.kilometresAnalyzed
    return ((real / 250.0) * 0.45 + (gross / 350.0) * 0.25 + (distance / 120.0) * 0.30).toFloat()
}

private fun String.extractCurrencyValue(): Double =
    replace("R$", "")
        .replace(".", "")
        .replace(",", ".")
        .trim()
        .toDoubleOrNull()
        ?: 0.0

private fun gpsStatusLabel(snapshot: CurrentLocationServiceSnapshot): String = when (snapshot.status) {
    CurrentLocationServiceStatus.IDLE -> "OFF"
    CurrentLocationServiceStatus.PERMISSION_MISSING -> "Permissão"
    CurrentLocationServiceStatus.PROVIDER_UNAVAILABLE -> "Indisp."
    else -> "%.1f km".format(java.util.Locale.forLanguageTag("pt-BR"), snapshot.sessionDistanceMeters / 1_000.0)
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(HomeScreenState(readerEnabled = true), {}, {}, {}, {})
    }
}
