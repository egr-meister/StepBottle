package com.egrmeister.stepbottle.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.stepbottle.AppContainer
import com.egrmeister.stepbottle.data.local.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class SettingsUiState(val loading: Boolean = true, val settings: AppSettings = AppSettings())

class SettingsViewModel(private val c: AppContainer) : ViewModel() {

    val state: StateFlow<SettingsUiState> = c.settings.settings
        .map { SettingsUiState(loading = false, settings = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Shortcut changes affect future entries only; existing records are untouched. */
    fun setWaterShortcuts(values: List<Int>) = viewModelScope.launch { c.settings.setWaterShortcuts(values) }
    fun setActivityShortcuts(values: List<Int>) = viewModelScope.launch { c.settings.setActivityShortcuts(values) }

    fun setReminders(enabled: Boolean, time: LocalTime) = viewModelScope.launch {
        val saved = c.settings.updateReminders(enabled, time)
        if (enabled) c.reminderScheduler.reschedule(saved) else c.reminderScheduler.cancelAll()
    }

    fun markPermissionAsked() = viewModelScope.launch { c.settings.setPermissionAsked() }

    fun clearAll(onDone: () -> Unit) = viewModelScope.launch {
        c.clearAllData()
        onDone()
    }
}
