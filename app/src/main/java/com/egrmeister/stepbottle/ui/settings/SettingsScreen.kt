package com.egrmeister.stepbottle.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.stepbottle.core.Format
import com.egrmeister.stepbottle.domain.activity.ActivityLimits
import com.egrmeister.stepbottle.domain.reminders.ReminderAvailability
import com.egrmeister.stepbottle.domain.time.NumberResult
import com.egrmeister.stepbottle.domain.water.WaterLimits
import com.egrmeister.stepbottle.reminders.ReminderScheduler
import com.egrmeister.stepbottle.ui.components.ConfirmDialog
import com.egrmeister.stepbottle.ui.components.Hint
import com.egrmeister.stepbottle.ui.components.SettingsSection
import com.egrmeister.stepbottle.ui.components.SwitchRow
import com.egrmeister.stepbottle.ui.components.TimePickerDialog

@Composable
fun SettingsScreen(vm: SettingsViewModel, onOpenPrivacy: () -> Unit, onCleared: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    var waterDialog by rememberSaveable { mutableStateOf(false) }
    var activityDialog by rememberSaveable { mutableStateOf(false) }
    var timePicker by rememberSaveable { mutableStateOf(false) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick++
    }

    if (s.loading) return
    val settings = s.settings
    val r = settings.reminders
    val status = remember(resumeTick, r.enabled) { ReminderScheduler.status(context, r.enabled) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

            SettingsSection("Water shortcuts")
            Text(settings.waterShortcuts.joinToString(" · ") { Format.ml(it) }, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { waterDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit water shortcuts") }

            SettingsSection("Activity shortcuts")
            Text(settings.activityShortcuts.joinToString(" · ") { Format.minutes(it) }, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { activityDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit activity shortcuts") }
            Hint("Changing shortcuts affects future entries only.")

            SettingsSection("Reminder")
            SwitchRow(
                label = "Daily logging reminder",
                checked = r.enabled,
                onChange = { on ->
                    vm.setReminders(on, r.time)
                    if (on && ReminderAvailability.shouldRequestPermission(
                            Build.VERSION.SDK_INT,
                            ReminderScheduler.hasPostPermission(context),
                            settings.notificationPermissionAsked,
                        )
                    ) {
                        vm.markPermissionAsked()
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            if (r.enabled) {
                OutlinedButton(onClick = { timePicker = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Time: ${Format.time(r.time)}")
                }
                ReminderAvailability.explanation(status)?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Open notification settings") }
                }
            }
            Hint(
                "One local reminder a day asks whether you'd like to update your log. Delivery time is approximate: " +
                    "Android may delay it to save battery. If StepBottle is force-stopped, reminders pause until you open the app again.",
            )

            SettingsSection("Privacy")
            Hint("All records are entered manually and stay on this device. No account, no internet access, no analytics.")
            OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Privacy information") }

            SettingsSection("Data")
            OutlinedButton(onClick = { clearDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Clear all local data", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (waterDialog) {
        ShortcutDialog(
            title = "Water shortcuts",
            unitLabel = "mL",
            hint = "Each shortcut: ${WaterLimits.MIN_ML}–${Format.number(WaterLimits.MAX_ML)} mL, whole numbers.",
            initial = settings.waterShortcuts,
            parse = WaterLimits::parse,
            onDismiss = { waterDialog = false },
            onSave = {
                vm.setWaterShortcuts(it)
                waterDialog = false
            },
        )
    }
    if (activityDialog) {
        ShortcutDialog(
            title = "Activity shortcuts",
            unitLabel = "minutes",
            hint = "Each shortcut: ${ActivityLimits.MIN_MINUTES}–${ActivityLimits.MAX_MINUTES} minutes, whole numbers.",
            initial = settings.activityShortcuts,
            parse = ActivityLimits::parse,
            onDismiss = { activityDialog = false },
            onSave = {
                vm.setActivityShortcuts(it)
                activityDialog = false
            },
        )
    }
    if (timePicker) {
        TimePickerDialog("Reminder time", r.time, onDismiss = { timePicker = false }) { t ->
            timePicker = false
            vm.setReminders(true, t)
        }
    }
    if (clearDialog) {
        ConfirmDialog(
            title = "Clear all local data?",
            text = "This permanently deletes every water and activity record, your shortcuts and reminder settings on this device, " +
                "and cancels the reminder. Defaults are restored.",
            confirmLabel = "Clear data",
            destructive = true,
            onConfirm = {
                clearDialog = false
                vm.clearAll(onCleared)
            },
            onDismiss = { clearDialog = false },
        )
    }
}

@Composable
private fun ShortcutDialog(
    title: String,
    unitLabel: String,
    hint: String,
    initial: List<Int>,
    parse: (String) -> NumberResult,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit,
) {
    var t1 by rememberSaveable { mutableStateOf(initial[0].toString()) }
    var t2 by rememberSaveable { mutableStateOf(initial[1].toString()) }
    var t3 by rememberSaveable { mutableStateOf(initial[2].toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val fields = listOf(t1, t2, t3)
    val setters: List<(String) -> Unit> = listOf({ t1 = it }, { t2 = it }, { t3 = it })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                fields.forEachIndexed { i, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { v ->
                            setters[i](v.filter { it.isDigit() }.take(4))
                            error = null
                        },
                        label = { Text("Shortcut ${i + 1} ($unitLabel)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Text(
                    error ?: hint,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = fields.map(parse)
                val bad = parsed.filterIsInstance<NumberResult.Invalid>().firstOrNull()
                if (bad != null) {
                    error = bad.message
                } else {
                    onSave(parsed.map { (it as NumberResult.Valid).value })
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
