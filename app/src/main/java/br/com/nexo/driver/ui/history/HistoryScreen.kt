package br.com.nexo.driver.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.journey.RideHistoryDecision
import br.com.nexo.driver.journey.RideHistoryEntry
import br.com.nexo.driver.journey.RideHistoryStatus
import br.com.nexo.driver.journey.formatBrlCompact
import br.com.nexo.driver.offer.OfferSource

data class HistoryScreenState(
    val enabled: Boolean = false,
    val entries: List<RideHistoryEntry> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    state: HistoryScreenState,
    onEnabledChanged: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onRideStatusChanged: (String, RideHistoryStatus) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val acceptedCount = state.entries.count {
        it.status in setOf(RideHistoryStatus.ACCEPTED, RideHistoryStatus.IN_RIDE, RideHistoryStatus.COMPLETED)
    }
    val rejectedCount = state.entries.count { it.status == RideHistoryStatus.REJECTED }
    val realProfit = state.entries.sumOf { it.estimatedRealProfitCents ?: 0L }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Histórico", fontWeight = FontWeight.SemiBold) })
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HistorySummaryCard(
                    enabled = state.enabled,
                    total = state.entries.size,
                    accepted = acceptedCount,
                    rejected = rejectedCount,
                    realProfit = realProfit.formatBrlCompact(),
                    onEnabledChanged = onEnabledChanged,
                    onClearHistory = onClearHistory,
                )
            }
            if (state.entries.isEmpty()) {
                item { EmptyHistoryCard() }
            } else {
                items(state.entries, key = { it.id }) { entry ->
                    HistoryEntryCard(entry = entry, onRideStatusChanged = onRideStatusChanged)
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(
    enabled: Boolean,
    total: Int,
    accepted: Int,
    rejected: Int,
    realProfit: String,
    onEnabledChanged: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
) {
    NeonHistoryCard(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        backgroundTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Corridas e ganhos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("$total registros", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChanged)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryTile("Aceitas", accepted.toString(), Modifier.weight(1f))
            SummaryTile("Recusadas", rejected.toString(), Modifier.weight(1f))
            SummaryTile("Lucro real", realProfit, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {},
                enabled = false,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Ver detalhes")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onClearHistory,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            ) {
                Text("Limpar histórico")
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(74.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    NeonHistoryCard {
        Text("Nenhum registro ainda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "As corridas analisadas aparecerão aqui quando o histórico estiver ligado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HistoryEntryCard(
    entry: RideHistoryEntry,
    onRideStatusChanged: (String, RideHistoryStatus) -> Unit,
) {
    val accent = when (entry.decision) {
        RideHistoryDecision.ACCEPT -> MaterialTheme.colorScheme.primary
        RideHistoryDecision.ANALYZE -> MaterialTheme.colorScheme.tertiary
        RideHistoryDecision.REJECT -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.routeLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(entry.source.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Text(entry.grossCents?.formatBrlCompact() ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text(entry.decision.label()) }, border = null)
                AssistChip(onClick = {}, label = { Text(entry.status.label()) }, border = null)
                entry.estimatedRealProfitCents?.let {
                    AssistChip(onClick = {}, label = { Text("${it.formatBrlCompact()} real") }, border = null)
                }
                entry.totalDistanceMeters?.let {
                    AssistChip(onClick = {}, label = { Text("%.1f km".format(java.util.Locale.forLanguageTag("pt-BR"), it / 1_000.0)) }, border = null)
                }
                entry.totalDurationSeconds?.let {
                    AssistChip(onClick = {}, label = { Text("${(it + 59) / 60} min") }, border = null)
                }
            }
            when (entry.status) {
                RideHistoryStatus.ACCEPTED -> OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onRideStatusChanged(entry.id, RideHistoryStatus.IN_RIDE) },
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Marcar em corrida") }
                RideHistoryStatus.IN_RIDE -> Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onRideStatusChanged(entry.id, RideHistoryStatus.COMPLETED) },
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Concluir corrida") }
                else -> Unit
            }
        }
    }
}

@Composable
private fun NeonHistoryCard(
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    backgroundTint: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(backgroundTint, Color.Transparent)))
                .padding(18.dp),
            content = { content() },
        )
    }
}

private fun RideHistoryEntry.routeLabel(): String =
    listOfNotNull(pickupAddress?.takeIf { it.isNotBlank() }, dropoffAddress?.takeIf { it.isNotBlank() })
        .joinToString(" → ")
        .ifBlank { "Endereço não identificado" }

private fun RideHistoryDecision.label(): String = when (this) {
    RideHistoryDecision.ACCEPT -> "Aceita"
    RideHistoryDecision.ANALYZE -> "Analisada"
    RideHistoryDecision.REJECT -> "Recusada"
}

private fun RideHistoryStatus.label(): String = when (this) {
    RideHistoryStatus.ANALYZED -> "Aguardando status"
    RideHistoryStatus.ACCEPTED -> "Aceita"
    RideHistoryStatus.REJECTED -> "Recusada"
    RideHistoryStatus.IN_RIDE -> "Em corrida"
    RideHistoryStatus.COMPLETED -> "Concluída"
}

private fun OfferSource.label(): String = when (this) {
    OfferSource.UBER -> "Uber"
    OfferSource.NINETY_NINE -> "99"
}

@Preview(showBackground = true, widthDp = 390, heightDp = 840)
@Composable
private fun HistoryScreenPreview() {
    MaterialTheme {
        HistoryScreen(
            state = HistoryScreenState(
                enabled = true,
                entries = listOf(
                    RideHistoryEntry(
                        id = "1",
                        detectedAtEpochMs = 0L,
                        decision = RideHistoryDecision.ACCEPT,
                        source = OfferSource.UBER,
                        grossCents = 3240,
                        totalDistanceMeters = 14_800,
                        totalDurationSeconds = 1_920,
                        pickupAddress = "Rua XV de Novembro",
                        dropoffAddress = "Batel",
                        estimatedRealProfitCents = 1840,
                        status = RideHistoryStatus.COMPLETED,
                    ),
                    RideHistoryEntry(
                        id = "2",
                        detectedAtEpochMs = 1L,
                        decision = RideHistoryDecision.REJECT,
                        source = OfferSource.NINETY_NINE,
                        grossCents = 1910,
                        totalDistanceMeters = 9_500,
                        totalDurationSeconds = 1_320,
                        pickupAddress = "Av. das Torres",
                        dropoffAddress = "Boa Vista",
                        estimatedRealProfitCents = 320,
                        status = RideHistoryStatus.REJECTED,
                    ),
                ),
            ),
            onEnabledChanged = {},
            onClearHistory = {},
            onRideStatusChanged = { _, _ -> },
        )
    }
}
