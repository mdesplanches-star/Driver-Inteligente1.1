package br.com.nexo.driver.geofence

fun interface RegionMembershipSubscription : AutoCloseable {
    override fun close()
}

/** Process-local observable state of which registered regions the driver is currently inside. */
object RegionMembershipRepository {
    private val lock = Any()
    private val observers = linkedSetOf<(List<RegionMembership>) -> Unit>()
    private var memberships: List<RegionMembership> = emptyList()

    fun current(): List<RegionMembership> = synchronized(lock) { memberships }

    fun update(next: List<RegionMembership>) {
        val listeners = synchronized(lock) {
            memberships = next
            observers.toList()
        }
        listeners.forEach { it(next) }
    }

    fun subscribe(observer: (List<RegionMembership>) -> Unit): RegionMembershipSubscription {
        val initial = synchronized(lock) {
            observers += observer
            memberships
        }
        observer(initial)
        return RegionMembershipSubscription { synchronized(lock) { observers -= observer } }
    }
}
