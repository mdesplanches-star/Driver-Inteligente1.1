package br.com.nexo.driver.destination

import android.content.Context
import android.content.SharedPreferences

/**
 * Private on-device storage for the driver's selected destination.
 *
 * Values are committed synchronously because a destination change must be visible to the capture
 * service immediately. Invalid or corrupt persisted data is treated as absent instead of crashing
 * the offer-reading flow.
 */
class SharedPreferencesDriverDestinationStore private constructor(
    private val preferences: SharedPreferences,
) : DriverDestinationStore {
    private val lock = Any()

    override fun load(): DriverDestination? = synchronized(lock) {
        DestinationPayloadCodec.decode(preferences.getString(KEY_DESTINATION, null))
    }

    override fun save(destination: DriverDestination): DriverDestination = synchronized(lock) {
        val validated = requireNotNull(destination.validatedOrNull()) {
            "A destination must have valid coordinates and a non-negative finite arrival radius."
        }
        check(
            preferences.edit()
                .putString(KEY_DESTINATION, DestinationPayloadCodec.encode(validated))
                .commit(),
        ) { "Could not persist driver destination." }
        validated
    }

    override fun clear() = synchronized(lock) {
        check(preferences.edit().remove(KEY_DESTINATION).commit()) {
            "Could not clear driver destination."
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "driver_destination"
        private const val KEY_DESTINATION = "destination_v1"

        fun create(context: Context): SharedPreferencesDriverDestinationStore =
            SharedPreferencesDriverDestinationStore(
                context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
    }
}
