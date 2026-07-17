package br.com.nexo.driver.ui.fuel

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import br.com.nexo.driver.fuel.FuelProfile
import br.com.nexo.driver.fuel.FuelProfileSnapshot
import br.com.nexo.driver.fuel.FuelType
import java.util.Locale

/**
 * Vehicle efficiency profiles used only to estimate fuel/energy consumption from the GPS session
 * distance already tracked locally. No sensor reading, no network call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFuelScreen(
    snapshot: FuelProfileSnapshot,
    onNavigateBack: () -> Unit,
    onSave: (FuelProfile) -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var vehicleLabel by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf(FuelType.FLEX) }
    var consumptionInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Veículo e combustível", fontWeight = FontWeight.SemiBold) },
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
                "O consumo é estimado a partir da distância de GPS da sessão e do rendimento médio " +
                    "informado abaixo. Nenhum dado sai do aparelho.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Novo veículo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = vehicleLabel,
                        onValueChange = { vehicleLabel = it },
                        label = { Text("Nome do veículo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FuelTypeChoiceGroup(selected = fuelType, onSelected = { fuelType = it })
                    OutlinedTextField(
                        value = consumptionInput,
                        onValueChange = { consumptionInput = it },
                        label = { Text("Consumo médio (km/${fuelType.unitLabel()})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Preço por ${fuelType.unitLabel()} em R$ (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    validationError?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            val consumption = consumptionInput.toDecimalOrNull()
                            when {
                                vehicleLabel.isBlank() -> validationError = "Informe um nome para o veículo."
                                consumption == null || consumption <= 0.0 ->
                                    validationError = "Informe um consumo válido, maior que zero."
                                else -> {
                                    validationError = null
                                    val priceCents = priceInput.toDecimalOrNull()?.let { reais -> Math.round(reais * 100) }
                                    onSave(
                                        FuelProfile.create(
                                            vehicleLabel = vehicleLabel,
                                            fuelType = fuelType,
                                            consumptionKmPerUnit = consumption,
                                            fuelPricePerUnitCents = priceCents,
                                            nowEpochMs = System.currentTimeMillis(),
                                        ),
                                    )
                                    vehicleLabel = ""
                                    consumptionInput = ""
                                    priceInput = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Salvar veículo")
                    }
                }
            }

            Text("Veículos salvos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (snapshot.profiles.isEmpty()) {
                Text(
                    "Nenhum veículo cadastrado ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            snapshot.profiles.forEach { profile ->
                FuelProfileRow(
                    profile = profile,
                    isActive = profile.id == snapshot.activeProfileId,
                    onSetActive = { onSetActive(profile.id) },
                    onDelete = { onDelete(profile.id) },
                )
            }
        }
    }
}

@Composable
private fun FuelProfileRow(
    profile: FuelProfile,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(profile.vehicleLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${profile.fuelType.label()} · ${
                        "%.1f".format(Locale.forLanguageTag("pt-BR"), profile.consumptionKmPerUnit)
                    } km/${profile.fuelType.unitLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isActive) {
                OutlinedButton(onClick = onSetActive) { Text("Usar") }
                Spacer(Modifier.width(8.dp))
            }
            TextButton(onClick = onDelete) { Text("Remover") }
        }
    }
}

@Composable
private fun FuelTypeChoiceGroup(selected: FuelType, onSelected: (FuelType) -> Unit) {
    Column(Modifier.selectableGroup()) {
        FuelType.entries.forEach { option ->
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

private fun FuelType.label(): String = when (this) {
    FuelType.GASOLINE -> "Gasolina"
    FuelType.ETHANOL -> "Etanol"
    FuelType.DIESEL -> "Diesel"
    FuelType.CNG -> "GNV"
    FuelType.ELECTRIC -> "Elétrico"
    FuelType.FLEX -> "Flex"
}

private fun FuelType.unitLabel(): String = if (this == FuelType.ELECTRIC) "kWh" else "l"

private fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Preview(showBackground = true)
@Composable
private fun VehicleFuelScreenPreview() {
    MaterialTheme {
        VehicleFuelScreen(
            snapshot = FuelProfileSnapshot(emptyList(), null),
            onNavigateBack = {},
            onSave = {},
            onDelete = {},
            onSetActive = {},
        )
    }
}
