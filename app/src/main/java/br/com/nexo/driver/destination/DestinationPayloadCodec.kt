package br.com.nexo.driver.destination

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/** Internal, versioned single-record codec; it keeps this small configuration dependency-free. */
internal object DestinationPayloadCodec {
    private const val SCHEMA = "driver-destination-v1"

    fun encode(destination: DriverDestination): String {
        val validated = requireNotNull(destination.validatedOrNull()) { "Invalid destination." }
        return listOf(
            SCHEMA,
            validated.coordinate.latitude.toString(),
            validated.coordinate.longitude.toString(),
            validated.arrivalRadiusMeters.toString(),
            validated.label?.let(::encodeText).orEmpty(),
        ).joinToString(separator = "\t")
    }

    fun decode(payload: String?): DriverDestination? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val fields = payload.split('\t')
            require(fields.size == 5 && fields[0] == SCHEMA)
            DriverDestination(
                coordinate = GeoCoordinate(
                    latitude = fields[1].toDouble(),
                    longitude = fields[2].toDouble(),
                ),
                arrivalRadiusMeters = fields[3].toDouble(),
                label = fields[4].takeIf { it.isNotEmpty() }?.let(::decodeText),
            ).validatedOrNull() ?: error("Invalid destination.")
        }.getOrNull()
    }

    private fun encodeText(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(UTF_8))

    private fun decodeText(value: String): String = String(Base64.getUrlDecoder().decode(value), UTF_8)
}
