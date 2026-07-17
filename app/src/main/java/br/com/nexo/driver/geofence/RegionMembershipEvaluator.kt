package br.com.nexo.driver.geofence

import br.com.nexo.driver.geo.GeoMath

/** Immutable, explainable membership result for a single registered region. */
data class RegionMembership(
    val region: Region,
    val distanceMeters: Double,
    val isInside: Boolean,
)

/**
 * Pure, deterministic membership check against manually registered regions. It only reports
 * distance/inside-or-not for enabled regions and makes no accept/reject decision — that is left
 * for a future feature the driver will design separately.
 */
class RegionMembershipEvaluator {
    fun evaluate(latitude: Double, longitude: Double, regions: List<Region>): List<RegionMembership> =
        regions.filter { it.isEnabled }.map { region ->
            val distance = GeoMath.haversineMeters(latitude, longitude, region.centerLatitude, region.centerLongitude)
            RegionMembership(region = region, distanceMeters = distance, isInside = distance <= region.radiusMeters)
        }
}
