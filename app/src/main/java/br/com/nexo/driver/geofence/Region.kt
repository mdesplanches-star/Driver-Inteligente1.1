package br.com.nexo.driver.geofence

import java.util.UUID

enum class RegionType { GOOD, BAD }

/**
 * A manually registered geographic region the driver marks as favorable or unfavorable.
 * Detection only reports membership (see [RegionMembershipEvaluator]); no automatic
 * accept/reject decision is derived from it.
 */
data class Region(
    val id: String,
    val name: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusMeters: Double,
    val type: RegionType,
    val isEnabled: Boolean = true,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(id.isNotBlank()) { "Region id cannot be blank." }
        require(name.isNotBlank()) { "Region name cannot be blank." }
        require(name.length <= MAX_NAME_LENGTH) { "Region name is too long." }
        require(centerLatitude.isFinite() && centerLatitude in -90.0..90.0) { "Invalid latitude." }
        require(centerLongitude.isFinite() && centerLongitude in -180.0..180.0) { "Invalid longitude." }
        require(radiusMeters.isFinite() && radiusMeters in MIN_RADIUS_METERS..MAX_RADIUS_METERS) {
            "Radius must be between $MIN_RADIUS_METERS and $MAX_RADIUS_METERS meters."
        }
        require(createdAtEpochMs > 0) { "Creation time must be positive." }
        require(updatedAtEpochMs >= createdAtEpochMs) { "Update time cannot precede creation time." }
    }

    fun updated(
        name: String = this.name,
        centerLatitude: Double = this.centerLatitude,
        centerLongitude: Double = this.centerLongitude,
        radiusMeters: Double = this.radiusMeters,
        type: RegionType = this.type,
        isEnabled: Boolean = this.isEnabled,
        updatedAtEpochMs: Long,
    ) = copy(
        name = name.trim(),
        centerLatitude = centerLatitude,
        centerLongitude = centerLongitude,
        radiusMeters = radiusMeters,
        type = type,
        isEnabled = isEnabled,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    companion object {
        const val MAX_NAME_LENGTH = 80
        const val MIN_RADIUS_METERS = 100.0
        const val MAX_RADIUS_METERS = 50_000.0
        const val DEFAULT_RADIUS_METERS = 1_000.0

        fun create(
            name: String,
            centerLatitude: Double,
            centerLongitude: Double,
            type: RegionType,
            radiusMeters: Double = DEFAULT_RADIUS_METERS,
            nowEpochMs: Long,
            id: String = UUID.randomUUID().toString(),
        ) = Region(
            id = id,
            name = name.trim(),
            centerLatitude = centerLatitude,
            centerLongitude = centerLongitude,
            radiusMeters = radiusMeters,
            type = type,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
    }
}

data class RegionSnapshot(
    val regions: List<Region>,
)
