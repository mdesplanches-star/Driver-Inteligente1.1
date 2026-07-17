package br.com.nexo.driver.journey

import android.content.Context

interface RideHistoryStore {
    fun load(): RideHistorySnapshot
    fun setEnabled(enabled: Boolean): RideHistorySnapshot
    fun record(entry: RideHistoryEntry): RideHistorySnapshot
    fun updateStatus(id: String, status: RideHistoryStatus): RideHistorySnapshot
    fun clear(): RideHistorySnapshot
}

class SharedPreferencesRideHistoryStore private constructor(
    context: Context,
) : RideHistoryStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun load(): RideHistorySnapshot = synchronized(lock) { read() }

    override fun setEnabled(enabled: Boolean): RideHistorySnapshot = synchronized(lock) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).commitOrThrow()
        read()
    }

    override fun record(entry: RideHistoryEntry): RideHistorySnapshot = synchronized(lock) {
        val current = read()
        if (!current.enabled) return current
        val nextEntries = (listOf(entry) + current.entries.filterNot { it.id == entry.id })
            .sortedByDescending { it.detectedAtEpochMs }
            .take(MAX_ENTRIES)
        writeEntries(nextEntries)
        current.copy(entries = nextEntries)
    }

    override fun updateStatus(id: String, status: RideHistoryStatus): RideHistorySnapshot = synchronized(lock) {
        val current = read()
        val nextEntries = current.entries.map { entry ->
            if (entry.id == id) entry.copy(status = status) else entry
        }
        writeEntries(nextEntries)
        current.copy(entries = nextEntries)
    }

    override fun clear(): RideHistorySnapshot = synchronized(lock) {
        writeEntries(emptyList())
        read()
    }

    private fun read(): RideHistorySnapshot = RideHistorySnapshot(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        entries = RideHistoryPayloadCodec.decode(preferences.getString(KEY_ENTRIES, null)),
    )

    private fun writeEntries(entries: List<RideHistoryEntry>) {
        preferences.edit().putString(KEY_ENTRIES, RideHistoryPayloadCodec.encode(entries)).commitOrThrow()
    }

    companion object {
        private const val PREFERENCES = "driver_ride_history"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ENTRIES = "entries_v1"
        private const val MAX_ENTRIES = 300

        fun create(context: Context): SharedPreferencesRideHistoryStore = SharedPreferencesRideHistoryStore(context)
    }
}

class DailyCostSettingsStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): DailyDriverCostSettings =
        DailyDriverCostSettingsCodec.decode(preferences.getString(KEY_SETTINGS, null))

    fun save(settings: DailyDriverCostSettings): DailyDriverCostSettings {
        preferences.edit().putString(KEY_SETTINGS, DailyDriverCostSettingsCodec.encode(settings)).commitOrThrow()
        return settings
    }

    companion object {
        private const val PREFERENCES = "driver_daily_cost_settings"
        private const val KEY_SETTINGS = "settings_v1"

        fun create(context: Context): DailyCostSettingsStore = DailyCostSettingsStore(context)
    }
}

class RideSessionClockStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): RideSessionClockState =
        RideSessionClockCodec.decode(preferences.getString(KEY_STATE, null))

    fun save(state: RideSessionClockState): RideSessionClockState {
        preferences.edit().putString(KEY_STATE, RideSessionClockCodec.encode(state)).commitOrThrow()
        return state
    }

    fun setReaderActive(active: Boolean, nowEpochMs: Long = System.currentTimeMillis()): RideSessionClockState =
        save(load().withReaderActive(active, nowEpochMs))

    fun setRideActive(active: Boolean, nowEpochMs: Long = System.currentTimeMillis()): RideSessionClockState =
        save(load().withRideActive(active, nowEpochMs))

    companion object {
        private const val PREFERENCES = "driver_session_clock"
        private const val KEY_STATE = "state_v1"

        fun create(context: Context): RideSessionClockStore = RideSessionClockStore(context)
    }
}

class AutoDecisionSettingsStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): AutoDecisionSettings =
        AutoDecisionSettingsCodec.decode(preferences.getString(KEY_SETTINGS, null))

    fun save(settings: AutoDecisionSettings): AutoDecisionSettings {
        preferences.edit().putString(KEY_SETTINGS, AutoDecisionSettingsCodec.encode(settings)).commitOrThrow()
        return settings
    }

    companion object {
        private const val PREFERENCES = "driver_auto_decision_settings"
        private const val KEY_SETTINGS = "settings_v1"

        fun create(context: Context): AutoDecisionSettingsStore = AutoDecisionSettingsStore(context)
    }
}

class DriverGoalSettingsStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): DriverGoalSettings =
        DriverGoalSettingsCodec.decode(preferences.getString(KEY_SETTINGS, null))

    fun save(settings: DriverGoalSettings): DriverGoalSettings {
        preferences.edit().putString(KEY_SETTINGS, DriverGoalSettingsCodec.encode(settings)).commitOrThrow()
        return settings
    }

    companion object {
        private const val PREFERENCES = "driver_goal_settings"
        private const val KEY_SETTINGS = "settings_v1"

        fun create(context: Context): DriverGoalSettingsStore = DriverGoalSettingsStore(context)
    }
}

private fun android.content.SharedPreferences.Editor.commitOrThrow() {
    check(commit()) { "Could not persist journey data." }
}
