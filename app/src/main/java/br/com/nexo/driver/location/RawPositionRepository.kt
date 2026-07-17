package br.com.nexo.driver.location

/** A single in-memory coordinate reading, never persisted and never logged. */
data class RawPosition(
    val latitude: Double,
    val longitude: Double,
    val capturedAtEpochMs: Long,
)

fun interface RawPositionSubscription : AutoCloseable {
    override fun close()
}

/**
 * Process-local, in-memory-only raw position channel used exclusively by local geofence
 * evaluation (see `br.com.nexo.driver.geofence`). Deliberately separate from
 * [CurrentLocationStateRepository], whose public snapshot is presentation-safe by design and must
 * never expose coordinates. This repository never persists a value, never leaves the process, and
 * is cleared whenever the location service goes idle.
 */
object RawPositionRepository {
    private val lock = Any()
    private val observers = linkedSetOf<(RawPosition?) -> Unit>()
    private var position: RawPosition? = null

    fun current(): RawPosition? = synchronized(lock) { position }

    fun update(next: RawPosition?) {
        val listeners = synchronized(lock) {
            position = next
            observers.toList()
        }
        listeners.forEach { it(next) }
    }

    fun subscribe(observer: (RawPosition?) -> Unit): RawPositionSubscription {
        val initial = synchronized(lock) {
            observers += observer
            position
        }
        observer(initial)
        return RawPositionSubscription { synchronized(lock) { observers -= observer } }
    }
}
