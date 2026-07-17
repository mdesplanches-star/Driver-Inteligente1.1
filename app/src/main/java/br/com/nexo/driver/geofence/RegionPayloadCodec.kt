package br.com.nexo.driver.geofence

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/** Internal, versioned codec intentionally based on Kotlin/JDK only (no JSON dependency required). */
internal object RegionPayloadCodec {
    private const val SCHEMA = "driver-region-v1"
    private const val REGION = "g"

    fun encode(regions: List<Region>): String = buildString {
        append(SCHEMA)
        regions.forEach { region ->
            append('\n')
            append(REGION).append('\t')
            append(encodeText(region.id)).append('\t')
            append(encodeText(region.name)).append('\t')
            append(region.centerLatitude).append('\t')
            append(region.centerLongitude).append('\t')
            append(region.radiusMeters).append('\t')
            append(region.type.name).append('\t')
            append(if (region.isEnabled) '1' else '0').append('\t')
            append(region.createdAtEpochMs).append('\t')
            append(region.updatedAtEpochMs)
        }
    }

    fun decode(payload: String?): List<Region> {
        if (payload.isNullOrBlank()) return emptyList()
        val lines = payload.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() != SCHEMA) return emptyList()

        val regions = linkedMapOf<String, Region>()
        while (lines.hasNext()) {
            val parts = lines.next().split('\t')
            if (parts.firstOrNull() != REGION) continue
            parseRegion(parts)?.let { regions[it.id] = it }
        }
        return regions.values.toList()
    }

    private fun parseRegion(parts: List<String>): Region? = runCatching {
        require(parts.size == 10)
        Region(
            id = decodeText(parts[1]),
            name = decodeText(parts[2]),
            centerLatitude = parts[3].toDouble(),
            centerLongitude = parts[4].toDouble(),
            radiusMeters = parts[5].toDouble(),
            type = RegionType.valueOf(parts[6]),
            isEnabled = parts[7] == "1",
            createdAtEpochMs = parts[8].toLong(),
            updatedAtEpochMs = parts[9].toLong(),
        )
    }.getOrNull()

    private fun encodeText(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(UTF_8))

    private fun decodeText(value: String): String = String(Base64.getUrlDecoder().decode(value), UTF_8)
}
