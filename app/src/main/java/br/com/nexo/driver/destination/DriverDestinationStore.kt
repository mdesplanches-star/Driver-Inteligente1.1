package br.com.nexo.driver.destination

/**
 * Local persistence contract for the optional "destination home" feature.
 *
 * The stored coordinate is intentionally only a driver-selected destination; no offer, route, or
 * location-history data belongs in this store. Callers receive an immutable value on every read.
 */
interface DriverDestinationStore {
    fun load(): DriverDestination?

    /** Replaces the current destination after validating the coordinate and arrival radius. */
    fun save(destination: DriverDestination): DriverDestination

    /** Removes the destination completely. */
    fun clear()
}

/** Useful for previews and deterministic unit tests without Android framework dependencies. */
class InMemoryDriverDestinationStore(
    initialDestination: DriverDestination? = null,
) : DriverDestinationStore {
    private var destination = initialDestination?.validatedOrNull()

    @Synchronized
    override fun load(): DriverDestination? = destination

    @Synchronized
    override fun save(destination: DriverDestination): DriverDestination {
        val validated = requireNotNull(destination.validatedOrNull()) {
            "A destination must have valid coordinates and a non-negative finite arrival radius."
        }
        this.destination = validated
        return validated
    }

    @Synchronized
    override fun clear() {
        destination = null
    }
}

/** Normalizes optional presentation text and rejects values unsafe for distance calculations. */
internal fun DriverDestination.validatedOrNull(): DriverDestination? {
    if (!coordinate.isValid || !arrivalRadiusMeters.isFinite() || arrivalRadiusMeters < 0.0) {
        return null
    }
    return copy(label = label?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LABEL_LENGTH))
}

private const val MAX_LABEL_LENGTH = 240
