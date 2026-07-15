package br.com.nexo.driver.ui.destination

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.destination.DriverDestination
import br.com.nexo.driver.destination.GeoCoordinate
import br.com.nexo.driver.destination.offline.OfflineAddressPackageTsvCodec
import br.com.nexo.driver.offline.OfflineMapPackage
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme

/**
 * A deliberately small, offline-first destination setup. Coordinates can be copied from any map
 * the driver already uses; no address is sent to a server by this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDestinationScreen(
    currentDestination: DriverDestination?,
    currentOfflineMapPackage: OfflineMapPackage?,
    onNavigateBack: () -> Unit,
    onSave: (DriverDestination) -> Unit,
    onClear: () -> Unit,
    onOfflineMapImported: (OfflineMapPackage) -> Unit,
    onOfflineMapRemoved: (OfflineMapPackage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var label by remember(currentDestination) { mutableStateOf(currentDestination?.label.orEmpty()) }
    var latitude by remember(currentDestination) {
        mutableStateOf(currentDestination?.coordinate?.latitude?.toInput().orEmpty())
    }
    var longitude by remember(currentDestination) {
        mutableStateOf(currentDestination?.coordinate?.longitude?.toInput().orEmpty())
    }
    var radius by remember(currentDestination) {
        mutableStateOf(currentDestination?.arrivalRadiusMeters?.toInput() ?: "150")
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    var offlineImportMessage by remember { mutableStateOf<String?>(null) }
    val offlineMapLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val mapPackage = context.persistOfflineMapPackage(uri)
            if (mapPackage == null) {
                offlineImportMessage = "Não foi possível manter acesso a esse arquivo. Escolha o pacote novamente."
            } else {
                onOfflineMapImported(mapPackage)
                offlineImportMessage = "Pacote vinculado para uso offline."
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Destino casa", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { OutlinedButton(onClick = onNavigateBack) { Text("Voltar") } },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Salve um ponto para avaliar se o desembarque aproxima voce de casa.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Uso offline", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Este destino fica somente neste celular. Importe um pacote local de endereços para avaliar as ofertas sem internet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            OfflineMapPackageCard(
                mapPackage = currentOfflineMapPackage,
                statusMessage = offlineImportMessage,
                onImport = { offlineMapLauncher.launch(arrayOf("*/*")) },
                onRemove = {
                    currentOfflineMapPackage?.let(onOfflineMapRemoved)
                    offlineImportMessage = "Pacote offline removido deste aplicativo."
                },
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it; validationError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome do destino (opcional)") },
                placeholder = { Text("Ex.: Casa") },
                singleLine = true,
            )
            NumericField(latitude, { latitude = it; validationError = null }, "Latitude", "Ex.: -25.4284")
            NumericField(longitude, { longitude = it; validationError = null }, "Longitude", "Ex.: -49.2733")
            NumericField(radius, { radius = it; validationError = null }, "Raio de chegada (metros)", "150")
            validationError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val parsed = parseDestination(label, latitude, longitude, radius)
                    if (parsed == null) validationError = "Confira latitude, longitude e o raio de chegada."
                    else onSave(parsed)
                },
            ) { Text("Salvar destino") }
            if (currentDestination != null) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onClear) { Text("Remover destino") }
            }
        }
    }
}

@Composable
private fun OfflineMapPackageCard(
    mapPackage: OfflineMapPackage?,
    statusMessage: String?,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Pacote offline de endereços", fontWeight = FontWeight.SemiBold)
            if (mapPackage == null) {
                Text(
                    "Selecione um arquivo TSV do Driver Inteligente já baixado. O Android mantém apenas a permissão de leitura; o arquivo não é copiado nem enviado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(modifier = Modifier.fillMaxWidth(), onClick = onImport) {
                    Text("Selecionar pacote offline")
                }
            } else {
                Text(mapPackage.displayName, fontWeight = FontWeight.Medium)
                Text(
                    "${mapPackage.sizeBytes.toFileSizeText()} • acesso salvo neste celular",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(modifier = Modifier.fillMaxWidth(), onClick = onImport) {
                    Text("Trocar pacote")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRemove) {
                    Text("Remover pacote")
                }
            }
            statusMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NumericField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

private fun parseDestination(label: String, latitudeInput: String, longitudeInput: String, radiusInput: String): DriverDestination? {
    val latitude = latitudeInput.toDecimalOrNull() ?: return null
    val longitude = longitudeInput.toDecimalOrNull() ?: return null
    val radius = radiusInput.toDecimalOrNull() ?: return null
    val coordinate = GeoCoordinate(latitude, longitude)
    return DriverDestination(coordinate, label, radius)
        .takeIf { coordinate.isValid && radius.isFinite() && radius >= 0.0 }
}

private fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

private fun Double.toInput(): String = "%.6f".format(java.util.Locale.US, this).trimEnd('0').trimEnd('.')

private fun Context.persistOfflineMapPackage(uri: Uri): OfflineMapPackage? = runCatching {
    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // Opening the stream verifies that the selected document is readable without loading a large
    // map package in memory. The map engine can later reopen this same persisted URI.
    checkNotNull(contentResolver.openInputStream(uri)) { "Documento indisponível" }.use { }
    val packageBytes = checkNotNull(contentResolver.openInputStream(uri)).use(::readBoundedOfflineAddressPackage)
    OfflineAddressPackageTsvCodec.decode(packageBytes)
    val metadata = contentResolver.readOfflineMapMetadata(uri)
    OfflineMapPackage(
        contentUri = uri.toString(),
        displayName = metadata.displayName,
        sizeBytes = metadata.sizeBytes,
        importedAtEpochMs = System.currentTimeMillis(),
    )
}.getOrNull()

private fun readBoundedOfflineAddressPackage(input: java.io.InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        check(output.size() + read <= MAX_OFFLINE_ADDRESS_PACKAGE_BYTES) { "Pacote muito grande." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private data class OfflineMapMetadata(
    val displayName: String,
    val sizeBytes: Long?,
)

private fun android.content.ContentResolver.readOfflineMapMetadata(uri: Uri): OfflineMapMetadata {
    var displayName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "mapa-offline"
    var sizeBytes: Long? = null
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let { column ->
                    cursor.getString(column)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
            cursor.getColumnIndex(OpenableColumns.SIZE)
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let { sizeBytes = cursor.getLong(it).takeIf { value -> value >= 0L } }
        }
    }
    return OfflineMapMetadata(displayName = displayName, sizeBytes = sizeBytes)
}

private fun Long?.toFileSizeText(): String = when {
    this == null -> "tamanho não informado"
    this < 1_024L -> "$this B"
    this < 1_024L * 1_024L -> "${this / 1_024L} KB"
    this < 1_024L * 1_024L * 1_024L -> "${"%.1f".format(java.util.Locale.US, this / (1_024.0 * 1_024.0))} MB"
    else -> "${"%.2f".format(java.util.Locale.US, this / (1_024.0 * 1_024.0 * 1_024.0))} GB"
}

private const val MAX_OFFLINE_ADDRESS_PACKAGE_BYTES = 16 * 1024 * 1024

@Preview(showBackground = true)
@Composable
private fun HomeDestinationScreenPreview() {
    DriverInteligenteTheme {
        HomeDestinationScreen(
            currentDestination = null,
            currentOfflineMapPackage = null,
            onNavigateBack = {},
            onSave = {},
            onClear = {},
            onOfflineMapImported = {},
            onOfflineMapRemoved = {},
        )
    }
}
