package br.com.nexo.driver.geofence

import android.content.Context
import android.content.SharedPreferences

/**
 * Small, dependency-free local store for driver-registered good/bad regions.
 *
 * The versioned payload is committed in a single SharedPreferences editor transaction. Corrupt
 * or stale entries are ignored individually rather than preventing the app from loading valid
 * regions.
 */
class SharedPreferencesRegionStore private constructor(
    private val preferences: SharedPreferences,
) : RegionStore {
    private val lock = Any()

    override fun load(): RegionSnapshot = synchronized(lock) { readSnapshot() }

    override fun save(region: Region): RegionSnapshot = synchronized(lock) {
        val current = readSnapshot()
        val regions = (current.regions.filterNot { it.id == region.id } + region).sortedBy { it.createdAtEpochMs }
        val next = RegionSnapshot(regions)
        writeSnapshot(next)
        next
    }

    override fun delete(regionId: String): RegionSnapshot = synchronized(lock) {
        val next = RegionSnapshot(readSnapshot().regions.filterNot { it.id == regionId })
        writeSnapshot(next)
        next
    }

    private fun readSnapshot(): RegionSnapshot = RegionSnapshot(
        RegionPayloadCodec.decode(preferences.getString(KEY_REGIONS, null)).sortedBy { it.createdAtEpochMs },
    )

    private fun writeSnapshot(snapshot: RegionSnapshot) {
        check(
            preferences.edit()
                .putString(KEY_REGIONS, RegionPayloadCodec.encode(snapshot.regions))
                .commit(),
        ) { "Could not persist regions." }
    }

    companion object {
        private const val PREFERENCES_NAME = "driver_regions"
        private const val KEY_REGIONS = "regions_v1"

        fun create(context: Context): SharedPreferencesRegionStore = SharedPreferencesRegionStore(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
    }
}
