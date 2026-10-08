package com.egrmeister.stepbottle.ui.today

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.stepbottle.Feedback
import com.egrmeister.stepbottle.R
import com.egrmeister.stepbottle.core.Format
import com.egrmeister.stepbottle.domain.activity.ActivityCategory
import com.egrmeister.stepbottle.domain.summary.DailySummary
import com.egrmeister.stepbottle.domain.summary.EntryFilter
import com.egrmeister.stepbottle.domain.summary.LogItem
import com.egrmeister.stepbottle.ui.components.PastDatePickerDialog
import com.egrmeister.stepbottle.ui.components.SbIcon
import com.egrmeister.stepbottle.ui.theme.LocalCategoryColors
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

const val INTRO_TEXT = "Water and activity are entered manually."

@Composable
fun DayScreen(
    vm: DayViewModel,
    feedback: Flow<Feedback>,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenWater: (date: LocalDate, amountMl: Int?) -> Unit,
    onOpenActivity: (date: LocalDate, minutes: Int?, category: ActivityCategory?) -> Unit,
    onEditWater: (Long) -> Unit,
    onEditActivity: (Long) -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var sheetMinutes by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(vm) {
        vm.navEvents.collect { e ->
            when (e) {
                is DayNavEvent.OpenWaterEditor -> onOpenWater(e.date, e.amountMl)
                is DayNavEvent.OpenActivityEditor -> onOpenActivity(e.date, e.minutes, e.category)
            }
        }
    }
    LaunchedEffect(feedback) {
        feedback.collect { f ->
            val result = snackbar.showSnackbar(
                message = f.message,
                actionLabel = if (f.undoToken != null) "Undo" else null,
                withDismissAction = f.undoToken == null,
                duration = if (f.undoToken != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && f.undoToken != null) vm.undo(f.undoToken)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (s.loading) return@Scaffold
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 600.dp
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "date") {
                    DateSelector(
                        date = s.date,
                        isToday = s.isToday,
                        showBack = showBack,
                        onBack = onBack,
                        onPrevious = vm::previousDay,
                        onNext = vm::nextDay,
                        onPick = { showDatePicker = true },
                        onToday = vm::goToToday,
                        modifier = Modifier.widthIn(max = 900.dp),
                    )
                }
                if (!s.settings.introDismissed) {
                    item(key = "intro") {
                        IntroNote(onDismiss = vm::dismissIntro, modifier = Modifier.widthIn(max = 900.dp))
                    }
                }
                item(key = "cards") {
                    val water: @Composable (Modifier) -> Unit = { m ->
                        WaterCard(
                            totalMl = s.summary.waterMl,
                            count = s.summary.waterCount,
                            shortcuts = s.settings.waterShortcuts,
                            onQuick = { ml -> vm.quickWater(ml, System.nanoTime()) },
                            onAdd = vm::addWater,
                            modifier = m,
                        )
                    }
                    val activity: @Composable (Modifier) -> Unit = { m ->
                        ActivityCard(
                            minutes = s.summary.activityMinutes,
                            count = s.summary.activityCount,
                            shortcuts = s.settings.activityShortcuts,
                            onQuick = { min ->
                                if (!vm.quickActivityNeedsEditor(min)) {
                                    sheetMinutes = min
                                }
                            },
                            onAdd = vm::addActivity,
                            modifier = m,
                        )
                    }
                    if (wide) {
                        Row(
                            Modifier.widthIn(max = 900.dp).fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            water(Modifier.weight(1f).fillMaxHeight())
                            activity(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            water(Modifier.fillMaxWidth())
                            activity(Modifier.fillMaxWidth())
                        }
                    }
                }
                item(key = "summary") {
                    SummaryBlock(s.summary, Modifier.widthIn(max = 900.dp).fillMaxWidth())
                }
                item(key = "filters") {
                    FilterRow(s.filter, vm::setFilter, Modifier.widthIn(max = 900.dp).fillMaxWidth())
                }
                if (s.items.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            when {
                                s.summary.isEmpty -> DailySummary.EMPTY_DAY
                                s.filter == EntryFilter.WATER -> "No water entries for this day."
                                else -> "No activity entries for this day."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                } else {
                    items(s.items, key = { (if (it is LogItem.Water) "w" else "a") + it.id }) { item ->
                        LogRow(
                            item = item,
                            onOpen = {
                                when (item) {
                                    is LogItem.Water -> onEditWater(item.id)
                                    is LogItem.Activity -> onEditActivity(item.id)
                                }
                            },
                            onDelete = { vm.delete(item, System.nanoTime()) },
                            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                        )
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showDatePicker) {
        PastDatePickerDialog(
            initial = s.date,
            today = s.today,
            onDismiss = { showDatePicker = false },
            onPick = vm::selectDate,
        )
    }

    sheetMinutes?.let { minutes ->
        QuickActivitySheet(
            minutes = minutes,
            initialCategory = s.settings.lastCategory,
            onDismiss = { sheetMinutes = null },
            onConfirm = { category ->
                sheetMinutes = null
                vm.confirmQuickActivity(minutes, category, System.nanoTime())
            },
        )
    }
}

@Composable
private fun DateSelector(
    date: LocalDate,
    isToday: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            IconButton(onClick = onBack) { SbIcon(R.drawable.ic_back, "Back to Week") }
        }
        IconButton(onClick = onPrevious) { SbIcon(R.drawable.ic_chevron_left, "Previous day") }
        TextButton(
            onClick = onPick,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                contentDescription = "Selected date ${Format.longDate(date)}${if (isToday) ", today" else ""}. Choose another date."
            },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isToday) "Today" else Format.dayShort(date), style = MaterialTheme.typography.titleLarge)
                Text(Format.longDate(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onNext, enabled = !isToday) { SbIcon(R.drawable.ic_chevron_right, "Next day") }
        if (!isToday) {
            TextButton(onClick = onToday, modifier = Modifier.heightIn(min = 48.dp)) { Text("Today") }
        }
    }
}

@Composable
private fun IntroNote(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SbIcon(R.drawable.ic_info, null, Modifier.size(20.dp))
            Text(INTRO_TEXT, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("OK") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(
    title: String,
    @DrawableRes icon: Int,
    value: String,
    entriesLine: String,
    container: Color,
    onContainer: Color,
    accent: Color,
    quickLabels: List<String>,
    quickDescriptions: List<String>,
    onQuick: (Int) -> Unit,
    addLabel: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SbIcon(icon, null, Modifier.size(24.dp), tint = accent)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).semantics { heading() })
            }
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(value, style = MaterialTheme.typography.displaySmall)
                Text(entriesLine, style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickLabels.forEachIndexed { i, label ->
                    OutlinedButton(
                        onClick = { onQuick(i) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = quickDescriptions[i] },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = onContainer),
                    ) { Text(label) }
                }
            }
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            ) {
                SbIcon(R.drawable.ic_add, null, Modifier.size(18.dp))
                Text(addLabel, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun WaterCard(
    totalMl: Long,
    count: Int,
    shortcuts: List<Int>,
    onQuick: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCategoryColors.current
    CategoryCard(
        title = "Water",
        icon = R.drawable.ic_water,
        value = Format.ml(totalMl),
        entriesLine = Format.plural(count, "water entry", "water entries"),
        container = colors.waterContainer,
        onContainer = colors.onWaterContainer,
        accent = colors.waterAccent,
        quickLabels = shortcuts.map { "+${Format.ml(it)}" },
        quickDescriptions = shortcuts.map { "Add ${Format.ml(it)} of water" },
        onQuick = { i -> onQuick(shortcuts[i]) },
        addLabel = "Add Water",
        onAdd = onAdd,
        modifier = modifier,
    )
}

@Composable
private fun ActivityCard(
    minutes: Long,
    count: Int,
    shortcuts: List<Int>,
    onQuick: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCategoryColors.current
    CategoryCard(
        title = "Activity Minutes",
        icon = R.drawable.ic_activity,
        value = Format.minutes(minutes),
        entriesLine = Format.plural(count, "activity entry", "activity entries"),
        container = colors.activityContainer,
        onContainer = colors.onActivityContainer,
        accent = colors.activityAccent,
        quickLabels = shortcuts.map { "+${Format.minutes(it)}" },
        quickDescriptions = shortcuts.map { "Add ${Format.minutes(it)} of activity" },
        onQuick = { i -> onQuick(shortcuts[i]) },
        addLabel = "Add Activity",
        onAdd = onAdd,
        modifier = modifier,
    )
}

@Composable
private fun SummaryBlock(summary: DailySummary, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Daily Summary", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (summary.isEmpty) {
                Text(DailySummary.EMPTY_DAY, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    "Water: ${summary.waterLine}" + if (summary.waterCount > 0) " (${summary.waterCountLine})" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Activity: ${summary.activityLine}" + if (summary.activityCount > 0) " (${summary.activityCountLine})" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(selected: EntryFilter, onSelect: (EntryFilter) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Entries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EntryFilter.entries.forEach { f ->
                FilterChip(
                    selected = f == selected,
                    onClick = { onSelect(f) },
                    label = { Text(f.label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun LogRow(item: LogItem, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalCategoryColors.current
    val (icon, type, value, detail, tint) = when (item) {
        is LogItem.Water -> RowParts(R.drawable.ic_water, "Water", Format.ml(item.entry.amountMl), null, colors.waterAccent)
        is LogItem.Activity -> RowParts(
            R.drawable.ic_activity,
            "Activity",
            Format.minutes(item.entry.durationMinutes),
            listOfNotNull(item.entry.category.label, item.entry.label).joinToString(" · "),
            colors.activityAccent,
        )
    }
    val time = Format.time(item.localTime)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open details", onClick = onOpen)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$type, $value${detail?.let { ", $it" } ?: ""}, at $time"
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SbIcon(icon, null, Modifier.size(22.dp), tint = tint)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("$type · $value", style = MaterialTheme.typography.bodyLarge)
                    if (!detail.isNullOrEmpty()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { SbIcon(R.drawable.ic_delete, "Delete $type entry at $time") }
        }
    }
}

private data class RowParts(@param:DrawableRes val icon: Int, val type: String, val value: String, val detail: String?, val tint: Color)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickActivitySheet(
    minutes: Int,
    initialCategory: ActivityCategory,
    onDismiss: () -> Unit,
    onConfirm: (ActivityCategory) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var category by rememberSaveable(minutes) { mutableStateOf(initialCategory) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add ${Format.minutes(minutes)} of activity", style = MaterialTheme.typography.titleLarge)
            Text(
                "Logged now as a duration you report. StepBottle doesn't measure activity.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityCategory.entries.forEach { c ->
                    FilterChip(
                        selected = c == category,
                        onClick = { category = c },
                        label = { Text(c.label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cancel") }
                Button(onClick = { onConfirm(category) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Add") }
            }
            Box(Modifier.height(8.dp))
        }
    }
}
