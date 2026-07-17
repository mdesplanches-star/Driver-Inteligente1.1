package br.com.nexo.driver.fuel

/**
 * Local persistence contract for vehicle fuel/efficiency profiles.
 *
 * Implementations return whole snapshots so callers never need to combine a profile list and
 * active-profile value from separate reads.
 */
interface FuelProfileStore {
    fun load(): FuelProfileSnapshot

    /** Creates or replaces a profile, making the first saved profile active. */
    fun save(profile: FuelProfile): FuelProfileSnapshot

    /** Removes a profile and clears the active selection if it was selected. */
    fun delete(profileId: String): FuelProfileSnapshot

    /** Selects an existing profile; pass null to clear the selection. */
    fun setActiveProfile(profileId: String?): FuelProfileSnapshot
}

/** Useful for previews and deterministic unit tests. Production code should use SharedPreferencesFuelProfileStore. */
class InMemoryFuelProfileStore(
    initial: FuelProfileSnapshot = FuelProfileSnapshot(emptyList(), null),
) : FuelProfileStore {
    private var snapshot = initial

    @Synchronized
    override fun load(): FuelProfileSnapshot = snapshot

    @Synchronized
    override fun save(profile: FuelProfile): FuelProfileSnapshot {
        val updated = snapshot.profiles.filterNot { it.id == profile.id } + profile
        snapshot = FuelProfileSnapshot(updated, snapshot.activeProfileId ?: profile.id)
        return snapshot
    }

    @Synchronized
    override fun delete(profileId: String): FuelProfileSnapshot {
        val updated = snapshot.profiles.filterNot { it.id == profileId }
        snapshot = FuelProfileSnapshot(updated, snapshot.activeProfileId?.takeUnless { it == profileId })
        return snapshot
    }

    @Synchronized
    override fun setActiveProfile(profileId: String?): FuelProfileSnapshot {
        require(profileId == null || snapshot.profiles.any { it.id == profileId }) {
            "Cannot activate a fuel profile that is not stored."
        }
        snapshot = snapshot.copy(activeProfileId = profileId)
        return snapshot
    }
}
