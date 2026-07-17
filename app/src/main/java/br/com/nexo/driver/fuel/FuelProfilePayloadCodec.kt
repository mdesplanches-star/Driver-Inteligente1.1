package br.com.nexo.driver.fuel

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/** Internal, versioned codec intentionally based on Kotlin/JDK only (no JSON dependency required). */
internal object FuelProfilePayloadCodec {
    private const val SCHEMA = "fuel-profile-v1"
    private const val PROFILE = "f"

    fun encode(profiles: List<FuelProfile>): String = buildString {
        append(SCHEMA)
        profiles.forEach { profile ->
            append('\n')
            append(PROFILE).append('\t')
            append(encodeText(profile.id)).append('\t')
            append(encodeText(profile.vehicleLabel)).append('\t')
            append(profile.fuelType.name).append('\t')
            append(profile.consumptionKmPerUnit).append('\t')
            append(profile.fuelPricePerUnitCents ?: "").append('\t')
            append(if (profile.isEnabled) '1' else '0').append('\t')
            append(profile.createdAtEpochMs).append('\t')
            append(profile.updatedAtEpochMs)
        }
    }

    fun decode(payload: String?): List<FuelProfile> {
        if (payload.isNullOrBlank()) return emptyList()
        val lines = payload.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() != SCHEMA) return emptyList()

        val profiles = linkedMapOf<String, FuelProfile>()
        while (lines.hasNext()) {
            val parts = lines.next().split('\t')
            if (parts.firstOrNull() != PROFILE) continue
            parseProfile(parts)?.let { profiles[it.id] = it }
        }
        return profiles.values.toList()
    }

    private fun parseProfile(parts: List<String>): FuelProfile? = runCatching {
        require(parts.size == 9)
        FuelProfile(
            id = decodeText(parts[1]),
            vehicleLabel = decodeText(parts[2]),
            fuelType = FuelType.valueOf(parts[3]),
            consumptionKmPerUnit = parts[4].toDouble(),
            fuelPricePerUnitCents = parts[5].takeIf { it.isNotEmpty() }?.toLong(),
            isEnabled = parts[6] == "1",
            createdAtEpochMs = parts[7].toLong(),
            updatedAtEpochMs = parts[8].toLong(),
        )
    }.getOrNull()

    private fun encodeText(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(UTF_8))

    private fun decodeText(value: String): String = String(Base64.getUrlDecoder().decode(value), UTF_8)
}
