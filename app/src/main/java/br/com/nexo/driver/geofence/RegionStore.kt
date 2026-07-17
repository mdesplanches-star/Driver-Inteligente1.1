package br.com.nexo.driver.geofence

/** Local persistence contract for driver-registered good/bad regions. */
interface RegionStore {
    fun load(): RegionSnapshot

    /** Creates or replaces a region. */
    fun save(region: Region): RegionSnapshot

    /** Removes a region. */
    fun delete(regionId: String): RegionSnapshot
}

/** Useful for previews and deterministic unit tests. Production code should use SharedPreferencesRegionStore. */
class InMemoryRegionStore(initial: RegionSnapshot = RegionSnapshot(emptyList())) : RegionStore {
    private var snapshot = initial

    @Synchronized
    override fun load(): RegionSnapshot = snapshot

    @Synchronized
    override fun save(region: Region): RegionSnapshot {
        snapshot = RegionSnapshot(snapshot.regions.filterNot { it.id == region.id } + region)
        return snapshot
    }

    @Synchronized
    override fun delete(regionId: String): RegionSnapshot {
        snapshot = RegionSnapshot(snapshot.regions.filterNot { it.id == regionId })
        return snapshot
    }
}
