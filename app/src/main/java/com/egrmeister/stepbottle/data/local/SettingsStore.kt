package com.egrmeister.stepbottle.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.egrmeister.stepbottle.domain.activity.ActivityCategory
import com.egrmeister.stepbottle.domain.activity.ActivityLimits
import com.egrmeister.stepbottle.domain.reminders.ReminderSettings
import com.egrmeister.stepbottle.domain.summary.EntryFilter
import com.egrmeister.stepbottle.domain.water.WaterLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

data class AppSettings(
    val waterShortcuts: List<Int> = WaterLimits.DEFAULT_SHORTCUTS,
    val activityShortcuts: List<Int> = ActivityLimits.DEFAULT_SHORTCUTS,
    val lastCategory: ActivityCategory = ActivityCategory.WALK,
    val reminders: ReminderSettings = ReminderSettings(),
    val notificationPermissionAsked: Boolean = false,
    val introDismissed: Boolean = false,
    val filter: EntryFilter = EntryFilter.ALL,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Shortcuts, reminder settings, last activity category and lightweight UI state. Records live in Room. */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val water = listOf(intPreferencesKey("water_1"), intPreferencesKey("water_2"), intPreferencesKey("water_3"))
        val activity = listOf(intPreferencesKey("activity_1"), intPreferencesKey("activity_2"), intPreferencesKey("activity_3"))
        val lastCategory = stringPreferencesKey("last_category")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val reminderMinute = intPreferencesKey("reminder_minute_of_day")
        val reminderGeneration = longPreferencesKey("reminder_generation")
        val permissionAsked = booleanPreferencesKey("notification_permission_asked")
        val introDismissed = booleanPreferencesKey("intro_dismissed")
        val filter = stringPreferencesKey("entry_filter")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            waterShortcuts = Keys.water.mapIndexed { i, k ->
                p[k]?.takeIf { WaterLimits.isValid(it) } ?: WaterLimits.DEFAULT_SHORTCUTS[i]
            },
            activityShortcuts = Keys.activity.mapIndexed { i, k ->
                p[k]?.takeIf { ActivityLimits.isValid(it) } ?: ActivityLimits.DEFAULT_SHORTCUTS[i]
            },
            lastCategory = ActivityCategory.fromName(p[Keys.lastCategory]) ?: ActivityCategory.WALK,
            reminders = ReminderSettings(
                enabled = p[Keys.remindersEnabled] ?: false,
                time = minuteToTime(p[Keys.reminderMinute] ?: (ReminderSettings.DEFAULT_TIME.toSecondOfDay() / 60)),
                generation = p[Keys.reminderGeneration] ?: 0L,
            ),
            notificationPermissionAsked = p[Keys.permissionAsked] ?: false,
            introDismissed = p[Keys.introDismissed] ?: false,
            filter = EntryFilter.entries.firstOrNull { it.name == p[Keys.filter] } ?: EntryFilter.ALL,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setWaterShortcuts(values: List<Int>) = store.edit { p ->
        require(values.size == 3 && values.all { WaterLimits.isValid(it) })
        values.forEachIndexed { i, v -> p[Keys.water[i]] = v }
    }

    suspend fun setActivityShortcuts(values: List<Int>) = store.edit { p ->
        require(values.size == 3 && values.all { ActivityLimits.isValid(it) })
        values.forEachIndexed { i, v -> p[Keys.activity[i]] = v }
    }

    suspend fun setLastCategory(category: ActivityCategory) = store.edit { it[Keys.lastCategory] = category.name }
    suspend fun setIntroDismissed() = store.edit { it[Keys.introDismissed] = true }
    suspend fun setFilter(filter: EntryFilter) = store.edit { it[Keys.filter] = filter.name }
    suspend fun setPermissionAsked() = store.edit { it[Keys.permissionAsked] = true }

    /** Saves reminder settings as a new schedule generation and returns it. */
    suspend fun updateReminders(enabled: Boolean, time: LocalTime): ReminderSettings {
        var result = ReminderSettings()
        store.edit { p ->
            val generation = (p[Keys.reminderGeneration] ?: 0L) + 1
            p[Keys.remindersEnabled] = enabled
            p[Keys.reminderMinute] = time.hour * 60 + time.minute
            p[Keys.reminderGeneration] = generation
            result = ReminderSettings(enabled, LocalTime.of(time.hour, time.minute), generation)
        }
        return result
    }

    suspend fun clear() = store.edit { it.clear() }

    private fun minuteToTime(m: Int): LocalTime {
        val safe = m.coerceIn(0, 24 * 60 - 1)
        return LocalTime.of(safe / 60, safe % 60)
    }
}
