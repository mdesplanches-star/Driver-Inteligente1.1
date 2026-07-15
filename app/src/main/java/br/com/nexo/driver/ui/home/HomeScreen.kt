package br.com.nexo.driver.ui.home

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class HomeScreenState(
    val readerEnabled: Boolean = false,
    val activeProfileName: String = "Dia a dia",
    val activeProfileSummary: String = "R$/km ≥ 1,75 · R$/h ≥ 40",
    val homeDestination: String? = null,
    val homeDestinationDetails: String? = null,
    val kilometresAnalyzed: Int = 0,
    val offersEvaluated: Int = 0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeScreenState,
    onReaderEnabledChanged: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onConfigureHome: () -> Unit,
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
            ReaderCard(state.readerEnabled, onReaderEnabledChanged)
            ProfileCard(state.activeProfileName, state.activeProfileSummary, onOpenFilters)
            HomeDestinationCard(state.homeDestination, state.homeDestinationDetails, onConfigureHome)
            TodaySummary(state.kilometresAnalyzed, state.offersEvaluated)
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
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Leitor de ofertas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (enabled) "Ativo · leitura local" else "Pausado",
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
    SectionCard(onClick = onOpenFilters) {
        Text("Perfil ativo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeDestinationCard(
    destination: String?,
    details: String?,
    onConfigureHome: () -> Unit,
) {
    SectionCard(onClick = onConfigureHome) {
        Text("Destino casa", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            destination ?: "Configurar destino",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            details ?: "Informe as coordenadas e importe um pacote offline de endereços.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodaySummary(kilometres: Int, offers: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Hoje", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SectionCard {
            MetricRow("km analisados", kilometres.toString())
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            MetricRow("ofertas avaliadas", offers.toString())
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
private fun SectionCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = { content() })
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(HomeScreenState(readerEnabled = true), {}, {}, {})
    }
}
