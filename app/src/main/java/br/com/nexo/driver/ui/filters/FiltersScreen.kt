package br.com.nexo.driver.ui.filters

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.EvaluationMode
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.R

/**
 * Read-only presentation of a profile's rules. The hosting screen owns persistence and opens
 * the rule editor through [onRuleClick] and [onAddFilter].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersScreen(
    state: FiltersScreenState,
    onNavigateBack: () -> Unit,
    onProfileEnabledChange: (Boolean) -> Unit,
    onProfileSelected: (String) -> Unit = {},
    onCreateProfile: () -> Unit = {},
    onDeleteActiveProfile: () -> Unit = {},
    onRuleEnabledChange: (FilterRuleId, Boolean) -> Unit,
    onRuleDelete: (FilterRuleId) -> Unit = {},
    onRuleClick: (FilterRuleId) -> Unit,
    onAddFilter: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val groupedRules = state.groupedRules()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Filtros") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Voltar")
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ProfileSwitchCard(
                    profiles = state.profiles,
                    activeProfileId = state.activeProfileId,
                    profileName = state.profileName,
                    enabled = state.isProfileEnabled,
                    onEnabledChange = onProfileEnabledChange,
                    onProfileSelected = onProfileSelected,
                    onCreateProfile = onCreateProfile,
                    onDeleteActiveProfile = onDeleteActiveProfile,
                )
            }

            FilterSection.entries.forEach { section ->
                val sectionRules = groupedRules[section].orEmpty()
                if (sectionRules.isNotEmpty()) {
                    item(key = "section-${section.name}") {
                        Text(
                            text = section.label.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                        )
                    }
                    items(sectionRules, key = { it.rule.id.stableKey }) { item ->
                        FilterRuleCard(
                            item = item,
                            onEnabledChange = { onRuleEnabledChange(item.rule.id, it) },
                            onDelete = { onRuleDelete(item.rule.id) },
                            onClick = { onRuleClick(item.rule.id) },
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onAddFilter,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)),
                ) {
                    Text("+ Adicionar filtro")
                }
            }
        }
    }
}

@Composable
private fun ProfileSwitchCard(
    profiles: List<FilterProfilePresentation>,
    activeProfileId: String?,
    profileName: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onProfileSelected: (String) -> Unit,
    onCreateProfile: () -> Unit,
    onDeleteActiveProfile: () -> Unit,
) {
    NeonFilterCard(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        backgroundTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Perfil ativo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (enabled) "Filtros ligados" else "Filtros pausados",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (profiles.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    profiles.forEach { profile ->
                        AssistChip(
                            onClick = { onProfileSelected(profile.id) },
                            label = { Text(if (profile.id == activeProfileId) "${profile.name} ✓" else profile.name) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCreateProfile,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                ) {
                    Text("Novo perfil")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = profiles.size > 1,
                    onClick = onDeleteActiveProfile,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                ) {
                    Text("Remover perfil")
                }
            }
        }
    }
}

@Composable
private fun FilterRuleCard(
    item: FilterRulePresentation,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val accent = when {
        !item.rule.enabled -> MaterialTheme.colorScheme.outline
        item.rule.mode == EvaluationMode.ELIMINATORY -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.rule.enabled) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = if (item.rule.enabled) 0.45f else 0.25f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.valueText?.let { target ->
                        AssistChip(
                            onClick = onClick,
                            label = { Text(target) },
                            border = null,
                        )
                    }
                    AssistChip(
                        onClick = onClick,
                        label = { Text(item.comparisonText) },
                        border = null,
                    )
                    if (item.rule.mode == EvaluationMode.ELIMINATORY) {
                        AssistChip(
                            onClick = onClick,
                            label = { Text("Eliminatória") },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = item.rule.enabled, onCheckedChange = onEnabledChange)
                OutlinedButton(
                    onClick = onDelete,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Remover")
                }
            }
        }
    }
}

@Composable
private fun NeonFilterCard(
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    backgroundTint: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
        ),
        border = BorderStroke(1.dp, borderColor),
    ) {
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
}

@Preview(showBackground = true, widthDp = 390, heightDp = 840)
@Composable
private fun FiltersScreenPreview() {
    MaterialTheme {
        FiltersScreen(
            state = FiltersScreenState(
                profileName = "Curitiba — padrão",
                profiles = listOf(
                    FilterProfilePresentation("daily", "Dia a dia", isActive = true),
                    FilterProfilePresentation("rain", "Chuva", isActive = false),
                    FilterProfilePresentation("night", "Noite", isActive = false),
                ),
                activeProfileId = "daily",
                isProfileEnabled = true,
                rules = sampleRules(),
            ),
            onNavigateBack = {},
            onProfileEnabledChange = {},
            onRuleEnabledChange = { _, _ -> },
            onRuleClick = {},
            onAddFilter = {},
        )
    }
}

private fun sampleRules(): List<FilterRule> = listOf(
    FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 800),
    FilterRule(Metric.RATE_PER_HOUR, Comparator.AT_LEAST, target = 4_000),
    FilterRule(Metric.RATE_PER_KM, Comparator.AT_LEAST, target = 175),
    FilterRule(Metric.PICKUP_DURATION, Comparator.AT_MOST, target = 360),
    FilterRule(Metric.PICKUP_DISTANCE, Comparator.AT_MOST, target = 2_500),
    FilterRule(Metric.TRIP_DURATION, Comparator.AT_MOST, target = 1_800),
    FilterRule(Metric.TRIP_DURATION, Comparator.AT_LEAST, target = 300),
    FilterRule(Metric.TOTAL_DISTANCE, Comparator.AT_MOST, target = 12_000),
    FilterRule(Metric.TOTAL_DISTANCE, Comparator.AT_LEAST, target = 2_000),
    FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 480),
    FilterRule(Metric.HAS_MULTIPLE_STOPS, Comparator.IS_FALSE),
    FilterRule(Metric.IS_TOWARD_DESTINATION, Comparator.IS_TRUE, enabled = false),
)
