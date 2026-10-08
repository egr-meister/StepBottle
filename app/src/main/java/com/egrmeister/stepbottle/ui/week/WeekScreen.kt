package com.egrmeister.stepbottle.ui.week

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.stepbottle.R
import com.egrmeister.stepbottle.core.Format
import com.egrmeister.stepbottle.domain.summary.DayTotals
import com.egrmeister.stepbottle.domain.summary.WeeklyStats
import com.egrmeister.stepbottle.ui.components.Hint
import com.egrmeister.stepbottle.ui.components.SbIcon
import com.egrmeister.stepbottle.ui.theme.LocalCategoryColors
import java.time.LocalDate

@Composable
fun WeekScreen(vm: WeekViewModel, onOpenDate: (LocalDate) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val stats = s.stats ?: return
    val colors = LocalCategoryColors.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Week", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::earlier) { SbIcon(R.drawable.ic_chevron_left, "Earlier seven days") }
                Text(
                    "${Format.monthDay(s.start)} – ${Format.monthDay(s.end)}, ${s.end.year}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::later, enabled = !s.isCurrent) { SbIcon(R.drawable.ic_chevron_right, "Later seven days") }
            }
            if (!s.isCurrent) {
                TextButton(onClick = vm::current, modifier = Modifier.heightIn(min = 48.dp)) { Text("Show the last 7 days") }
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 600.dp
                val water = @Composable { m: Modifier ->
                    MiniChart(
                        title = "Water (mL)",
                        icon = R.drawable.ic_water,
                        days = stats.days,
                        value = { it.waterMl },
                        max = stats.maxWater,
                        format = { Format.number(it) },
                        spoken = { if (it.waterCount == 0) "no water entries" else Format.ml(it.waterMl) },
                        barColor = colors.waterAccent,
                        today = s.today,
                        onOpenDate = onOpenDate,
                        modifier = m,
                    )
                }
                val activity = @Composable { m: Modifier ->
                    MiniChart(
                        title = "Activity (minutes)",
                        icon = R.drawable.ic_activity,
                        days = stats.days,
                        value = { it.activityMinutes },
                        max = stats.maxActivity,
                        format = { Format.number(it) },
                        spoken = { if (it.activityCount == 0) "no activity entries" else Format.minutes(it.activityMinutes) },
                        barColor = colors.activityAccent,
                        today = s.today,
                        onOpenDate = onOpenDate,
                        modifier = m,
                    )
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        water(Modifier.weight(1f))
                        activity(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        water(Modifier.fillMaxWidth())
                        activity(Modifier.fillMaxWidth())
                    }
                }
            }

            DayTable(stats.days, s.today, onOpenDate)
            Totals(stats)
            Hint(
                "Totals count only what was entered. A day without entries is shown as “No entries”, " +
                    "not as zero water or inactivity. Averages describe your records and are not targets or health assessments.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MiniChart(
    title: String,
    @DrawableRes icon: Int,
    days: List<DayTotals>,
    value: (DayTotals) -> Long,
    max: Long,
    format: (Long) -> String,
    spoken: (DayTotals) -> String,
    barColor: Color,
    today: LocalDate,
    onOpenDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = "$title chart. " + days.joinToString("; ") { "${Format.shortDate(it.date)}: ${spoken(it)}" }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                SbIcon(icon, null, Modifier.size(20.dp), tint = barColor)
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp).semantics { heading() })
            }
            Hint("Scale: 0 – ${format(max)}", Modifier.clearAndSetSemantics { })
            Row(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { d ->
                    val v = value(d)
                    val fraction = if (max <= 0) 0f else (v.toFloat() / max).coerceIn(0f, 1f)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(role = Role.Button, onClickLabel = "Open daily log") { onOpenDate(d.date) }
                            .clearAndSetSemantics { },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (v > 0) format(v) else "–",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            if (fraction > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.7f)
                                        .fillMaxHeight(fraction.coerceAtLeast(0.03f))
                                        .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(
                            Format.dayShort(d.date).take(2),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (d.date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTable(days: List<DayTotals>, today: LocalDate, onOpenDate: (LocalDate) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clearAndSetSemantics { }) {
                Text("Date", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.2f))
                Text("Water", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("Activity", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            days.asReversed().forEach { d ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val waterText = if (d.waterCount == 0) "No entries" else Format.ml(d.waterMl)
                val activityText = if (d.activityCount == 0) "No entries" else Format.minutes(d.activityMinutes)
                val dateText = if (d.date == today) "Today" else Format.shortDate(d.date)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button, onClickLabel = "Open daily log") { onOpenDate(d.date) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$dateText. Water: $waterText. Activity: $activityText."
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dateText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1.2f))
                    Text(waterText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(activityText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun Totals(stats: WeeklyStats) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Seven-day totals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            StatLine("Total recorded water", Format.ml(stats.totalWaterMl))
            StatLine("Total recorded activity", Format.minutes(stats.totalActivityMinutes))
            StatLine("Days with water records", "${stats.daysWithWater} of 7")
            StatLine("Days with activity records", "${stats.daysWithActivity} of 7")
            StatLine(
                "Average water on days with water records",
                stats.averageWaterOnWaterDays?.let { Format.ml(it) } ?: "No entries",
            )
            StatLine(
                "Average activity on days with activity records",
                stats.averageActivityOnActivityDays?.let { Format.minutes(it) } ?: "No entries",
            )
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
