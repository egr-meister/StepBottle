package com.egrmeister.stepbottle.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.stepbottle.AppContainer
import com.egrmeister.stepbottle.UndoAction
import com.egrmeister.stepbottle.core.Format
import com.egrmeister.stepbottle.data.repository.SaveResult
import com.egrmeister.stepbottle.domain.activity.ActivityCategory
import com.egrmeister.stepbottle.domain.activity.ActivityLimits
import com.egrmeister.stepbottle.domain.activity.ActivityValidation
import com.egrmeister.stepbottle.domain.activity.ActivityValidator
import com.egrmeister.stepbottle.domain.water.WaterValidation
import com.egrmeister.stepbottle.domain.water.WaterValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class EntryKind { WATER, ACTIVITY }

data class EntryDraft(
    val loaded: Boolean = false,
    val kind: EntryKind = EntryKind.WATER,
    val isNew: Boolean = true,
    val amountText: String = "",
    val category: ActivityCategory = ActivityCategory.WALK,
    val labelText: String = "",
    val date: LocalDate = LocalDate.MIN,
    val time: LocalTime? = null,
    val amountError: String? = null,
    val labelError: String? = null,
    val timeError: String? = null,
    val saving: Boolean = false,
    val closed: Boolean = false,
    val missing: Boolean = false,
    val dirty: Boolean = false,
)

/**
 * Add/edit form for both entry types. The draft lives in SavedStateHandle so it survives
 * rotation and process recreation. Adding from a past date preselects that date and requires
 * an explicit time; nothing is silently recorded against today.
 */
class EntryEditorViewModel(
    private val c: AppContainer,
    private val handle: SavedStateHandle,
    private val kind: EntryKind,
    private val entryId: Long?,
    initialDate: LocalDate,
    prefillAmount: Int?,
    prefillCategory: ActivityCategory?,
) : ViewModel() {

    private val _state = MutableStateFlow(EntryDraft(kind = kind, isNew = entryId == null))
    val state: StateFlow<EntryDraft> = _state.asStateFlow()

    init {
        val restoredDate = handle.get<String>(K_DATE)
        if (restoredDate != null) {
            _state.value = EntryDraft(
                loaded = true,
                kind = kind,
                isNew = entryId == null,
                amountText = handle.get<String>(K_AMOUNT).orEmpty(),
                category = ActivityCategory.fromName(handle.get<String>(K_CATEGORY)) ?: ActivityCategory.WALK,
                labelText = handle.get<String>(K_LABEL).orEmpty(),
                date = LocalDate.parse(restoredDate),
                time = handle.get<String>(K_TIME)?.takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                dirty = handle.get<Boolean>(K_DIRTY) ?: false,
            )
        } else if (entryId == null) {
            viewModelScope.launch {
                val today = c.time.today()
                val date = if (initialDate.isAfter(today)) today else initialDate
                val time = if (date == today) c.time.localTimeNow().withSecond(0).withNano(0) else null
                setLoaded(
                    EntryDraft(
                        loaded = true,
                        kind = kind,
                        isNew = true,
                        amountText = prefillAmount?.toString().orEmpty(),
                        category = prefillCategory ?: c.settings.current().lastCategory,
                        date = date,
                        time = time,
                    ),
                )
            }
        } else {
            viewModelScope.launch {
                val draft = when (kind) {
                    EntryKind.WATER -> c.repository.getWater(entryId)?.let {
                        EntryDraft(
                            loaded = true,
                            kind = kind,
                            isNew = false,
                            amountText = it.amountMl.toString(),
                            date = it.localDate,
                            time = it.localTime.withSecond(0).withNano(0),
                        )
                    }
                    EntryKind.ACTIVITY -> c.repository.getActivity(entryId)?.let {
                        EntryDraft(
                            loaded = true,
                            kind = kind,
                            isNew = false,
                            amountText = it.durationMinutes.toString(),
                            category = it.category,
                            labelText = it.label.orEmpty(),
                            date = it.localDate,
                            time = it.localTime.withSecond(0).withNano(0),
                        )
                    }
                }
                if (draft == null) _state.value = EntryDraft(loaded = true, kind = kind, isNew = false, missing = true) else setLoaded(draft)
            }
        }
    }

    private fun setLoaded(d: EntryDraft) {
        _state.value = d
        persist(d)
    }

    private fun persist(d: EntryDraft) {
        handle[K_AMOUNT] = d.amountText
        handle[K_CATEGORY] = d.category.name
        handle[K_LABEL] = d.labelText
        handle[K_DATE] = d.date.toString()
        handle[K_TIME] = d.time?.toString().orEmpty()
        handle[K_DIRTY] = d.dirty
    }

    private fun edit(transform: (EntryDraft) -> EntryDraft) {
        _state.update { transform(it).copy(dirty = true) }
        persist(_state.value)
    }

    fun onAmount(text: String) = edit { it.copy(amountText = text.filter { ch -> ch.isDigit() }.take(5), amountError = null) }
    fun onCategory(category: ActivityCategory) = edit { it.copy(category = category) }
    fun onLabel(text: String) = edit { it.copy(labelText = text.replace("\n", " ").take(LABEL_INPUT_CAP), labelError = null) }
    fun onDate(date: LocalDate) = edit { it.copy(date = date, timeError = null) }
    fun onTime(time: LocalTime) = edit { it.copy(time = time, timeError = null) }

    fun today(): LocalDate = c.time.today()

    fun save() {
        val d = _state.value
        if (d.saving || !d.loaded || d.missing || d.closed) return
        val zone = c.time.zone()
        val now = c.time.now()
        when (kind) {
            EntryKind.WATER -> when (val v = WaterValidator.validate(d.amountText, d.date, d.time, zone, now)) {
                is WaterValidation.Invalid -> _state.update { it.copy(amountError = v.amountError, timeError = v.timeError) }
                is WaterValidation.Valid -> launchSave {
                    if (entryId == null) {
                        val id = c.repository.addWater(v.amountMl, v.zoned)
                        c.feedback.showUndoable(waterMessage(v.amountMl, v.zoned.toLocalDate()), UndoAction.RemoveAddedWater(id))
                        true
                    } else {
                        val r = c.repository.updateWater(entryId, v.amountMl, v.zoned)
                        if (r is SaveResult.Saved) c.feedback.show("Water entry updated.")
                        r !is SaveResult.NotFound || markMissing()
                    }
                }
            }
            EntryKind.ACTIVITY -> when (
                val v = ActivityValidator.validate(d.amountText, d.category, d.labelText, d.date, d.time, zone, now)
            ) {
                is ActivityValidation.Invalid -> _state.update {
                    it.copy(amountError = v.durationError, labelError = v.labelError, timeError = v.timeError)
                }
                is ActivityValidation.Valid -> launchSave {
                    val r = if (entryId == null) {
                        c.repository.addActivity(v.minutes, v.category, v.label, v.zoned)
                    } else {
                        c.repository.updateActivity(entryId, v.minutes, v.category, v.label, v.zoned)
                    }
                    when (r) {
                        is SaveResult.Saved -> {
                            c.settings.setLastCategory(v.category)
                            if (entryId == null) {
                                c.feedback.showUndoable(
                                    activityMessage(v.minutes, v.category, v.zoned.toLocalDate()),
                                    UndoAction.RemoveAddedActivity(r.id),
                                )
                            } else {
                                c.feedback.show("Activity entry updated.")
                            }
                            true
                        }
                        is SaveResult.DailyLimitExceeded -> {
                            _state.update { it.copy(amountError = ActivityLimits.dailyLimitMessage(r.otherMinutesThatDay)) }
                            false
                        }
                        SaveResult.NotFound -> markMissing()
                    }
                }
            }
        }
    }

    /** Deletes after the user confirmed; the deletion can still be undone once from the daily log. */
    fun delete() {
        val d = _state.value
        val id = entryId ?: return
        if (d.saving || d.closed) return
        launchSave {
            when (kind) {
                EntryKind.WATER -> c.repository.deleteWater(id)?.let {
                    c.feedback.showUndoable("Water entry deleted.", UndoAction.RestoreWater(it))
                }
                EntryKind.ACTIVITY -> c.repository.deleteActivity(id)?.let {
                    c.feedback.showUndoable("Activity entry deleted.", UndoAction.RestoreActivity(it))
                }
            }
            true
        }
    }

    private fun markMissing(): Boolean {
        _state.update { it.copy(missing = true) }
        return false
    }

    /** Runs one save at a time; [block] returns true when the form should close. */
    private fun launchSave(block: suspend () -> Boolean) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val close = try {
                block()
            } finally {
                _state.update { it.copy(saving = false) }
            }
            if (close) _state.update { it.copy(closed = true, dirty = false) }
        }
    }

    private fun suffix(date: LocalDate) = if (date == c.time.today()) "" else " for ${Format.shortDate(date)}"

    private fun waterMessage(ml: Int, date: LocalDate) = "${Format.ml(ml)} water recorded${suffix(date)}."

    private fun activityMessage(minutes: Int, category: ActivityCategory, date: LocalDate) =
        "${Format.minutes(minutes)} ${category.label.lowercase()} recorded${suffix(date)}."

    private companion object {
        const val LABEL_INPUT_CAP = 60
        const val K_AMOUNT = "draft_amount"
        const val K_CATEGORY = "draft_category"
        const val K_LABEL = "draft_label"
        const val K_DATE = "draft_date"
        const val K_TIME = "draft_time"
        const val K_DIRTY = "draft_dirty"
    }
}
