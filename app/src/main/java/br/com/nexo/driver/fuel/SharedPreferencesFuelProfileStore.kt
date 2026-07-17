package br.com.nexo.driver.fuel

import android.content.Context
import android.content.SharedPreferences

/**
 * Small, dependency-free local store for vehicle fuel profiles.
 *
 * One versioned payload and the selected id are committed in a single SharedPreferences editor
 * transaction. Corrupt or stale entries are ignored individually rather than preventing the app
 * from loading valid profiles.
 */
class SharedPreferencesFuelProfileStore private constructor(
    private val preferences: SharedPreferences,
) : FuelProfileStore {
    private val lock = Any()

    override fun load(): FuelProfileSnapshot = synchronized(lock) { readSnapshot() }

    override fun save(profile: FuelProfile): FuelProfileSnapshot = synchronized(lock) {
        val current = readSnapshot()
        val profiles = (current.profiles.filterNot { it.id == profile.id } + profile).sortedBy { it.createdAtEpochMs }
        val next = FuelProfileSnapshot(profiles, current.activeProfileId ?: profile.id)
        writeSnapshot(next)
        next
    }

    override fun delete(profileId: String): FuelProfileSnapshot = synchronized(lock) {
        val current = readSnapshot()
        val next = FuelProfileSnapshot(
            profiles = current.profiles.filterNot { it.id == profileId },
            activeProfileId = current.activeProfileId?.takeUnless { it == profileId },
        )
        writeSnapshot(next)
        next
    }

    override fun setActiveProfile(profileId: String?): FuelProfileSnapshot = synchronized(lock) {
        val current = readSnapshot()
        require(profileId == null || current.profiles.any { it.id == profileId }) {
            "Cannot activate a fuel profile that is not stored."
        }
        val next = current.copy(activeProfileId = profileId)
        writeSnapshot(next)
        next
    }

    private fun readSnapshot(): FuelProfileSnapshot {
        val profiles = FuelProfilePayloadCodec.decode(preferences.getString(KEY_PROFILES, null))
            .sortedBy { it.createdAtEpochMs }
        val activeId = preferences.getString(KEY_ACTIVE_PROFILE, null)?.takeIf { selected ->
            profiles.any { it.id == selected }
        }
        return FuelProfileSnapshot(profiles, activeId)
    }

    private fun writeSnapshot(snapshot: FuelProfileSnapshot) {
        check(
            preferences.edit()
                .putString(KEY_PROFILES, FuelProfilePayloadCodec.encode(snapshot.profiles))
                .putString(KEY_ACTIVE_PROFILE, snapshot.activeProfileId)
                .commit(),
        ) { "Could not persist fuel profiles." }
    }

    companion object {
        private const val PREFERENCES_NAME = "driver_fuel_profiles"
        private const val KEY_PROFILES = "fuel_profiles_v1"
        private const val KEY_ACTIVE_PROFILE = "active_fuel_profile_id"

        fun create(context: Context): SharedPreferencesFuelProfileStore = SharedPreferencesFuelProfileStore(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
    }
}
