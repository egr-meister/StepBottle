package com.egrmeister.stepbottle.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The interface is English; numbers and dates use US English formatting throughout. */
object Format {
    private val locale: Locale = Locale.US
    private val longDate = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale)
    private val shortDate = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
    private val dayShort = DateTimeFormatter.ofPattern("EEE", locale)
    private val monthDay = DateTimeFormatter.ofPattern("MMM d", locale)
    private val time = DateTimeFormatter.ofPattern("h:mm a", locale)

    fun number(value: Long): String = String.format(locale, "%,d", value)
    fun number(value: Int): String = number(value.toLong())
    fun ml(value: Long): String = "${number(value)} mL"
    fun ml(value: Int): String = ml(value.toLong())
    fun minutes(value: Long): String = if (value == 1L) "1 min" else "${number(value)} min"
    fun minutes(value: Int): String = minutes(value.toLong())
    fun longDate(date: LocalDate): String = longDate.format(date)
    fun shortDate(date: LocalDate): String = shortDate.format(date)
    fun dayShort(date: LocalDate): String = dayShort.format(date)
    fun monthDay(date: LocalDate): String = monthDay.format(date)
    fun time(t: LocalTime): String = time.format(t)

    fun plural(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "${number(count)} $many"
}
