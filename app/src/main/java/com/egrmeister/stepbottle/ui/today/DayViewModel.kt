package com.egrmeister.stepbottle.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.stepbottle.AppContainer
import com.egrmeister.stepbottle.UndoAction
import com.egrmeister.stepbottle.core.Format
import com.egrmeister.stepbottle.data.local.AppSettings
import com.egrmeister.stepbottle.data.repository.SaveResult
import com.egrmeister.stepbottle.domain.activity.ActivityCategory
import com.egrmeister.stepbottle.domain.activity.ActivityLimits
import com.egrmeister.stepbottle.domain.summary.DailySummary
import com.egrmeister.stepbottle.domain.summary.EntryFilter
import com.egrmeister.stepbottle.domain.summary.LogItem
import com.egrmeister.stepbottle.domain.summary.LogList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val summary: DailySummary = DailySummary(),
    val items: List<LogItem> = emptyList(),
    val filter: EntryFilter = EntryFilter.ALL,
    val settings: AppSettings = AppSettings(),
) {
    val isToday: Boolean get() = date == today
}

/** Requests that need another screen (historical quick entries require an explicit time). */
sealed interface DayNavEvent {
    data class OpenWaterEditor(val date: LocalDate, val amountMl: Int?) : DayNavEvent
    data class OpenActivityEditor(val date: LocalDate, val minutes: Int?, val category: ActivityCategory?) : DayNavEvent
}

/**
 * Daily log for one selected date. On the Today tab ([fixedDate] == null) the selection follows
 * the current date across midnight until the user picks another date.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(
    private val c: AppContainer,
    private val handle: SavedStateHandle,
    fixedDate: LocalDate?,
) : ViewModel() {

    private val followToday = MutableStateFlow(handle.get<Boolean>(K_FOLLOW) ?: (fixedDate == null))
    private val selected = MutableStateFlow(
        handle.get<String>(K_DATE)?.let(LocalDate::parse) ?: fixedDate ?: c.todayClock.today.value,
    )

    private val _nav = Channel<DayNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<DayNavEvent> = _nav.receiveAsFlow()

    init {
        viewModelScope.launch {
            c.todayClock.today.collect { today ->
                if (followToday.value) setSelected(today)
                if (selected.value.isAfter(today)) setSelected(today)
            }
        }
    }

    private val dayData = selected.flatMapLatest { date ->
        combine(c.repository.observeWater(date), c.repository.observeActivity(date)) { w, a -> Triple(date, w, a) }
    }

    val state: StateFlow<DayUiState> = combine(dayData, c.settings.settings, c.todayClock.today) { (date, w, a), s, today ->
        DayUiState(
            loading = false,
            date = date,
            today = today,
            summary = DailySummary.of(w, a),
            items = LogList.combine(w, a, s.filter),
            filter = s.filter,
            settings = s,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())

    private fun setSelected(date: LocalDate) {
        selected.value = date
        handle[K_DATE] = date.toString()
    }

    fun selectDate(date: LocalDate) {
        val today = c.todayClock.today.value
        val target = if (date.isAfter(today)) today else date
        followToday.value = target == today
        handle[K_FOLLOW] = followToday.value
        setSelected(target)
    }

    fun previousDay() = selectDate(selected.value.minusDays(1))
    fun nextDay() = selectDate(selected.value.plusDays(1))
    fun goToToday() = selectDate(c.todayClock.today.value)

    fun setFilter(filter: EntryFilter) = viewModelScope.launch { c.settings.setFilter(filter) }
    fun dismissIntro() = viewModelScope.launch { c.settings.setIntroDismissed() }

    /** Quick portion: logs now on Today; on a past date opens the editor so a time is chosen. */
    fun quickWater(amountMl: Int, activationId: Long) {
        val date = selected.value
        if (date != c.todayClock.today.value) {
            _nav.trySend(DayNavEvent.OpenWaterEditor(date, amountMl))
            return
        }
        if (!c.actionGuard.tryBegin(activationId)) return
        viewModelScope.launch {
            try {
                val id = c.repository.addWater(amountMl, c.time.zonedNow())
                c.feedback.showUndoable("${Format.ml(amountMl)} water recorded.", UndoAction.RemoveAddedWater(id))
            } finally {
                c.actionGuard.end()
            }
        }
    }

    /** Whether a quick duration should open the confirmation sheet (Today) or the editor (past date). */
    fun quickActivityNeedsEditor(minutes: Int): Boolean {
        val date = selected.value
        if (date == c.todayClock.today.value) return false
        _nav.trySend(DayNavEvent.OpenActivityEditor(date, minutes, state.value.settings.lastCategory))
        return true
    }

    /** Confirmed from the compact sheet: logs now, remembers the category. */
    fun confirmQuickActivity(minutes: Int, category: ActivityCategory, activationId: Long) {
        if (!c.actionGuard.tryBegin(activationId)) return
        viewModelScope.launch {
            try {
                c.settings.setLastCategory(category)
                when (val r = c.repository.addActivity(minutes, category, null, c.time.zonedNow())) {
                    is SaveResult.Saved -> c.feedback.showUndoable(
                        "${Format.minutes(minutes)} ${category.label.lowercase()} recorded.",
                        UndoAction.RemoveAddedActivity(r.id),
                    )
                    is SaveResult.DailyLimitExceeded -> c.feedback.show(ActivityLimits.dailyLimitMessage(r.otherMinutesThatDay))
                    SaveResult.NotFound -> Unit
                }
            } finally {
                c.actionGuard.end()
            }
        }
    }

    fun addWater() {
        _nav.trySend(DayNavEvent.OpenWaterEditor(selected.value, null))
    }

    fun addActivity() {
        _nav.trySend(DayNavEvent.OpenActivityEditor(selected.value, null, null))
    }

    fun delete(item: LogItem, activationId: Long) {
        if (!c.actionGuard.tryBegin(activationId)) return
        viewModelScope.launch {
            try {
                when (item) {
                    is LogItem.Water -> c.repository.deleteWater(item.id)?.let {
                        c.feedback.showUndoable("Water entry deleted.", UndoAction.RestoreWater(it))
                    }
                    is LogItem.Activity -> c.repository.deleteActivity(item.id)?.let {
                        c.feedback.showUndoable("Activity entry deleted.", UndoAction.RestoreActivity(it))
                    }
                }
            } finally {
                c.actionGuard.end()
            }
        }
    }

    fun undo(token: Long) = viewModelScope.launch { c.feedback.undo(token) }

    private companion object {
        const val K_DATE = "selected_date"
        const val K_FOLLOW = "follow_today"
    }
}
