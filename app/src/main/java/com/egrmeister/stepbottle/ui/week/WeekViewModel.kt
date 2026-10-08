package com.egrmeister.stepbottle.ui.week

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.stepbottle.AppContainer
import com.egrmeister.stepbottle.domain.summary.WeekCalculator
import com.egrmeister.stepbottle.domain.summary.WeeklyStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class WeekUiState(
    val loading: Boolean = true,
    val end: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val stats: WeeklyStats? = null,
) {
    val isCurrent: Boolean get() = end == today
    val start: LocalDate get() = WeekCalculator.startOf(end)
}

/**
 * Today and the previous six dates, with navigation to earlier seven-day ranges.
 * [offsetWeeks] = 0 follows the current date across midnight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    private val offsetWeeks = MutableStateFlow(handle.get<Int>(K_OFFSET) ?: 0)

    val state: StateFlow<WeekUiState> = combine(offsetWeeks, c.todayClock.today) { offset, today ->
        today to today.minusDays(offset * WeekCalculator.DAYS)
    }.flatMapLatest { (today, end) ->
        c.repository.observeWeek(end).map { WeekUiState(loading = false, end = end, today = today, stats = it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    private fun setOffset(value: Int) {
        val v = value.coerceAtLeast(0)
        offsetWeeks.value = v
        handle[K_OFFSET] = v
    }

    fun earlier() = setOffset(offsetWeeks.value + 1)
    fun later() = setOffset(offsetWeeks.value - 1)
    fun current() = setOffset(0)

    private companion object {
        const val K_OFFSET = "week_offset"
    }
}
