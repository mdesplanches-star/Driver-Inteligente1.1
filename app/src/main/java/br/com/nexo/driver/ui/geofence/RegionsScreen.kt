package br.com.nexo.driver.ui.geofence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.R
import br.com.nexo.driver.geofence.Region
import br.com.nexo.driver.geofence.RegionSnapshot
import br.com.nexo.driver.geofence.RegionType
import java.util.Locale

/**
 * Manual registration of favorable/unfavorable regions. This screen only records regions and
 * lets the driver see membership later; it never makes an accept/reject decision.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionsScreen(
    snapshot: RegionSnapshot,
    onNavigateBack: () -> Unit,
    onSave: (Region) -> Unit,
    onDelete: (String) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    insideRegionIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var latitudeInput by remember { mutableStateOf("") }
    var longitudeInput by remember { mutableStateOf("") }
    var radiusInput by remember { mutableStateOf(Region.DEFAULT_RADIUS_METERS.toInt().toString()) }
    var type by remember { mutableStateOf(RegionType.GOOD) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Regiões boas/ruins", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Voltar")
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "As coordenadas podem ser copiadas de qualquer mapa que você já usa. Tudo fica só " +
                    "neste aparelho; por enquanto o app apenas registra as regiões, sem tomar nenhuma decisão automática.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Nova região", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome da região") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = latitudeInput,
                            onValueChange = { latitudeInput = it },
                            label = { Text("Latitude") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = longitudeInput,
                            onValueChange = { longitudeInput = it },
                            label = { Text("Longitude") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = radiusInput,
                        onValueChange = { radiusInput = it },
                        label = { Text("Raio (metros)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RegionTypeChoiceGroup(selected = type, onSelected = { type = it })
                    validationError?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            val latitude = latitudeInput.toDecimalOrNull()
                            val longitude = longitudeInput.toDecimalOrNull()
                            val radius = radiusInput.toDecimalOrNull()
                            val result = runCatching {
                                require(name.isNotBlank()) { "Informe um nome para a região." }
                                requireNotNull(latitude) { "Informe uma latitude válida." }
                                requireNotNull(longitude) { "Informe uma longitude válida." }
                                requireNotNull(radius) { "Informe um raio válido." }
                                Region.create(
                                    name = name,
                                    centerLatitude = latitude,
                                    centerLongitude = longitude,
                                    type = type,
                                    radiusMeters = radius,
                                    nowEpochMs = System.currentTimeMillis(),
                                )
                            }
                            result.fold(
                                onSuccess = { region ->
                                    validationError = null
                                    onSave(region)
                                    name = ""
                                    latitudeInput = ""
                                    longitudeInput = ""
                                    radiusInput = Region.DEFAULT_RADIUS_METERS.toInt().toString()
                                },
                                onFailure = { failure ->
                                    validationError = failure.message ?: "Não foi possível salvar a região."
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Salvar região")
                    }
                }
            }

            Text("Regiões salvas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (snapshot.regions.isEmpty()) {
                Text(
                    "Nenhuma região cadastrada ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            snapshot.regions.forEach { region ->
                RegionRow(
                    region = region,
                    isInsideNow = region.id in insideRegionIds,
                    onEnabledChange = { enabled -> onEnabledChange(region.id, enabled) },
                    onDelete = { onDelete(region.id) },
                )
            }
        }
    }
}

@Composable
private fun RegionRow(
    region: Region,
    isInsideNow: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isInsideNow) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(region.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${region.type.label()} · raio ${
                            "%.0f".format(Locale.forLanguageTag("pt-BR"), region.radiusMeters)
                        } m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = region.isEnabled, onCheckedChange = onEnabledChange)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text("Remover") }
            }
            if (isInsideNow) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Você está aqui agora",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RegionTypeChoiceGroup(selected: RegionType, onSelected: (RegionType) -> Unit) {
    Column(Modifier.selectableGroup()) {
        RegionType.entries.forEach { option ->
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
                Text(option.label(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun RegionType.label(): String = when (this) {
    RegionType.GOOD -> "Região boa"
    RegionType.BAD -> "Região ruim"
}

private fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Preview(showBackground = true)
@Composable
private fun RegionsScreenPreview() {
    MaterialTheme {
        RegionsScreen(
            snapshot = RegionSnapshot(emptyList()),
            onNavigateBack = {},
            onSave = {},
            onDelete = {},
            onEnabledChange = { _, _ -> },
        )
    }
}
