package com.egrmeister.stepbottle.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.egrmeister.stepbottle.R
import com.egrmeister.stepbottle.ui.components.SbIcon
import com.egrmeister.stepbottle.ui.components.SectionTitle

private val sections = listOf(
    "Manual records" to "Every water and activity record is entered by you. StepBottle does not count steps, read sensors, " +
        "use GPS or location, connect to Health Connect or wearables, or detect exercise.",
    "Stored on this device" to "Records and settings are kept in the app's private storage on this device. " +
        "There is no account, no backend and no cloud synchronization.",
    "No network" to "StepBottle does not request internet access. It contains no ads, analytics, payments or tracking SDKs.",
    "Backup" to "Cloud backup and device-to-device transfer are disabled, so records do not leave this device that way. " +
        "Uninstalling the app or using Clear all local data deletes them permanently.",
    "Permissions" to "Notifications (optional, Android 13+) are used only for the daily logging reminder you can turn on. " +
        "Boot-completed is used only to restore that reminder after a restart. Logging works without either.",
    "Not medical advice" to "StepBottle is a diary. It does not give medical advice, calculate calories, score your health " +
        "or prescribe water amounts or exercise. Input limits are technical, not recommendations.",
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { SbIcon(R.drawable.ic_back, "Back") }
                Text("Privacy information", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            }
            sections.forEach { (title, body) ->
                SectionTitle(title)
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
