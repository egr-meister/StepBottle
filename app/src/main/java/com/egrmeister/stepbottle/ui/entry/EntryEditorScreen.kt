package com.egrmeister.stepbottle.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.stepbottle.R
import com.egrmeister.stepbottle.core.Format
import com.egrmeister.stepbottle.domain.activity.ActivityCategory
import com.egrmeister.stepbottle.domain.activity.ActivityLimits
import com.egrmeister.stepbottle.domain.water.WaterLimits
import com.egrmeister.stepbottle.ui.components.ConfirmDialog
import com.egrmeister.stepbottle.ui.components.Hint
import com.egrmeister.stepbottle.ui.components.PastDatePickerDialog
import com.egrmeister.stepbottle.ui.components.SbIcon
import com.egrmeister.stepbottle.ui.components.TimePickerDialog
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditorScreen(vm: EntryEditorViewModel, onClose: () -> Unit) {
    val d by vm.state.collectAsStateWithLifecycle()
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(d.closed) { if (d.closed) onClose() }

    val requestClose: () -> Unit = {
        if (d.dirty) {
            askDiscard = true
        } else {
            onClose()
        }
    }
    // Only intercepts Back while there are unsaved changes, so predictive Back works otherwise.
    BackHandler(enabled = d.dirty && !d.closed) { askDiscard = true }

    val water = d.kind == EntryKind.WATER
    val title = when {
        d.isNew && water -> "Add Water"
        d.isNew -> "Add Activity"
        water -> "Water entry"
        else -> "Activity entry"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = requestClose) { SbIcon(R.drawable.ic_back, "Cancel") } },
                actions = {
                    if (!d.isNew && d.loaded && !d.missing) {
                        IconButton(onClick = { askDelete = true }) { SbIcon(R.drawable.ic_delete, "Delete entry") }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!d.loaded) return@Column
            if (d.missing) {
                Text("This entry no longer exists.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onClose, modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp)) { Text("Close") }
                return@Column
            }
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (water) {
                    OutlinedTextField(
                        value = d.amountText,
                        onValueChange = vm::onAmount,
                        label = { Text("Amount (mL)") },
                        singleLine = true,
                        isError = d.amountError != null,
                        supportingText = {
                            Text(d.amountError ?: "Whole milliliters, ${WaterLimits.MIN_ML}–${Format.number(WaterLimits.MAX_ML)} mL. This is an input limit, not a recommendation.")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = d.amountText,
                        onValueChange = vm::onAmount,
                        label = { Text("Duration (minutes)") },
                        singleLine = true,
                        isError = d.amountError != null,
                        supportingText = {
                            Text(d.amountError ?: "Whole minutes, ${ActivityLimits.MIN_MINUTES}–${ActivityLimits.MAX_MINUTES} per entry; at most 1,440 per day.")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityCategory.entries.forEach { c ->
                            FilterChip(
                                selected = d.category == c,
                                onClick = { vm.onCategory(c) },
                                label = { Text(c.label) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = d.labelText,
                        onValueChange = vm::onLabel,
                        label = { Text("Label (optional)") },
                        singleLine = true,
                        isError = d.labelError != null,
                        supportingText = {
                            Text(d.labelError ?: "${d.labelText.trim().length}/${ActivityLimits.LABEL_MAX} characters")
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text("Date: ${Format.shortDate(d.date)}")
                    }
                    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(d.time?.let { "Time: ${Format.time(it)}" } ?: "Choose time")
                    }
                }
                d.timeError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (!water) {
                    Hint("The time places this entry in your diary. It isn't a recorded workout start or end time.")
                }
                Hint(if (water) "Water is entered manually." else "Activity minutes are durations you report. StepBottle doesn't measure or detect activity.")

                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = requestClose, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cancel") }
                    Button(onClick = vm::save, enabled = !d.saving, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Save") }
                }
            }
        }
    }

    if (showDate) {
        PastDatePickerDialog(initial = d.date, today = vm.today(), onDismiss = { showDate = false }, onPick = vm::onDate)
    }
    if (showTime) {
        TimePickerDialog(
            title = "Time",
            initial = d.time ?: LocalTime.of(12, 0),
            onDismiss = { showTime = false },
        ) {
            vm.onTime(it)
            showTime = false
        }
    }
    if (askDiscard) {
        AlertDiscard(
            onDiscard = {
                askDiscard = false
                onClose()
            },
            onKeep = { askDiscard = false },
        )
    }
    if (askDelete) {
        ConfirmDialog(
            title = "Delete this entry?",
            text = "It is removed from this day's summary and weekly totals. You can undo right after.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                askDelete = false
                vm.delete()
            },
            onDismiss = { askDelete = false },
        )
    }
}

@Composable
private fun AlertDiscard(onDiscard: () -> Unit, onKeep: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Discard changes?") },
        text = { Text("Your unsaved changes to this entry will be lost.") },
        confirmButton = { TextButton(onClick = onDiscard) { Text("Discard") } },
        dismissButton = { TextButton(onClick = onKeep) { Text("Keep editing") } },
    )
}
