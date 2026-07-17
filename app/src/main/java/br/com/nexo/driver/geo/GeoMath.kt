package br.com.nexo.driver.geo

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared great-circle distance calculation used by every module that reasons about two WGS-84
 * points (session movement, home destination, direction guidance, geofence membership). Kept
 * free of any module's coordinate type so it has no package dependency in either direction.
 */
object GeoMath {
    private const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8

    /** Haversine great-circle distance in meters using the IUGG mean Earth radius. */
    fun haversineMeters(fromLatitude: Double, fromLongitude: Double, toLatitude: Double, toLongitude: Double): Double {
        val latitudeDelta = (toLatitude - fromLatitude).toRadians()
        val longitudeDelta = (toLongitude - fromLongitude).toRadians()
        val fromLatitudeRadians = fromLatitude.toRadians()
        val toLatitudeRadians = toLatitude.toRadians()
        val a = (sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(fromLatitudeRadians) * cos(toLatitudeRadians) * sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
            ).coerceIn(0.0, 1.0)
        return EARTH_MEAN_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
}
