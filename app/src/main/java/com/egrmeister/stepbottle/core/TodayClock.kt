package com.egrmeister.stepbottle.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate

/**
 * Current local date, refreshed at midnight, on foreground return and on time/zone changes.
 * Refreshing never touches stored records.
 */
class TodayClock(private val time: TimeProvider, scope: CoroutineScope) {
    private val _today = MutableStateFlow(time.today())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                val now = time.zonedNow()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                val wait = Duration.between(now, nextMidnight).toMillis().coerceIn(1_000L, 60 * 60 * 1000L)
                delay(wait)
                refresh()
            }
        }
    }

    fun refresh() {
        _today.value = time.today()
    }
}
