package org.openscreentime.parent.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale
import org.openscreentime.shared.model.AppCountTrend
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.WeekSummary
import org.openscreentime.shared.model.WeeklyReport
import org.openscreentime.shared.model.computeWeeklyReport
import org.openscreentime.shared.model.topAppCountTrends
import org.openscreentime.shared.model.formatDuration
import org.openscreentime.shared.model.todayDateString
import org.openscreentime.shared.repo.FamilyRepository

private const val REPORT_WEEKS = 4
private const val REPORT_LOOKBACK_DAYS = REPORT_WEEKS * 7
private const val TOP_APPS_SHOWN = 5

/**
 * A rolling 4-week screen-time report, reached from ChildDetailScreen rather than shown
 * on the Dashboard - see #29's design discussion: exact numbers stay one tap away rather
 * than glanceable, the same reasoning behind the Dashboard's calm status card. Modeled
 * loosely on Apple's and Android's own screen-time reports: a weekly-totals bar chart plus
 * a by-app breakdown, bounded to a fixed lookback rather than an open-ended history one
 * could pore over.
 */
@Composable
fun WeeklyReportScreen(
    repository: FamilyRepository,
    childId: String,
    onBack: () -> Unit
) {
    val parentUid = repository.currentUid ?: return
    var child by remember { mutableStateOf<ChildProfile?>(null) }
    var todayStats by remember { mutableStateOf(DailyStats(date = todayDateString())) }
    var historicalStats by remember { mutableStateOf<List<DailyStats>>(emptyList()) }

    DisposableEffect(childId) {
        val childReg = repository.listenChildren(parentUid) { list ->
            child = list.firstOrNull { it.id == childId }
        }
        val statsReg = repository.listenDailyStats(parentUid, childId, todayDateString()) { todayStats = it }
        onDispose {
            childReg.remove()
            statsReg.remove()
        }
    }

    LaunchedEffect(childId) {
        historicalStats = repository.getRecentDailyStats(parentUid, childId, REPORT_LOOKBACK_DAYS)
    }

    val report = remember(todayStats, historicalStats) {
        computeWeeklyReport(historicalStats + todayStats, weeks = REPORT_WEEKS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${child?.name ?: "Weekly"} report") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { WhyOnlyHereCard() }
            item { WeeklyTotalsSection(report) }
            item { AppTrendsSection(report) }
            val trackUnlocks = child?.trackUnlocks == true
            val trackNotifications = child?.trackNotifications == true
            if (trackUnlocks) item { UnlocksSection(report) }
            if (trackNotifications) item { NotificationsSection(report) }
            if (trackUnlocks || trackNotifications) {
                item { DailyDigestSection(report, showUnlocks = trackUnlocks, showNotifications = trackNotifications) }
            }
        }
    }
}

/**
 * See #30 - names the asymmetry explicitly rather than leaving a parent to wonder why the
 * kid app only ever shows a calm status icon: this level of detail is deliberately
 * parent-only, meant to inform a conversation, not to run silent surveillance.
 */
@Composable
private fun WhyOnlyHereCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Text(
            "Your child only ever sees a simple status icon, never this report or exact " +
                "numbers - that's deliberate, so screen time stays something to be aware of, " +
                "not something to obsess over. Use what you see here to start a conversation " +
                "with them, not to track them silently.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun WeeklyTotalsSection(report: WeeklyReport) {
    val chronological = report.weeks.reversed()
    val barColor = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(16.dp)) {
        Text("Total screen time", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        val overallAverage = if (chronological.isEmpty()) 0L else {
            chronological.sumOf { it.totalScreenTimeMs } / chronological.size
        }
        Text(
            "Weekly average: ${formatDuration(overallAverage)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        BarChart(values = chronological.map { it.totalScreenTimeMs }, color = barColor, modifier = Modifier.fillMaxWidth().height(120.dp))
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            chronological.forEach { week ->
                Text(
                    formatWeekLabel(week),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AppTrendsSection(report: WeeklyReport) {
    val topApps = topAppsAcrossWeeks(report, TOP_APPS_SHOWN)
    val barColor = MaterialTheme.colorScheme.secondary
    Column(Modifier.padding(16.dp)) {
        Text("By app", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "The apps used most over the last $REPORT_WEEKS weeks, oldest to newest.",
            style = MaterialTheme.typography.bodySmall
        )
        if (topApps.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("No app usage synced yet.", style = MaterialTheme.typography.bodySmall)
        }
        topApps.forEach { (packageName, appName) ->
            val weeklyTotals = report.weeks.reversed().map { week ->
                week.byApp.firstOrNull { it.packageName == packageName }?.totalMs ?: 0L
            }
            Spacer(Modifier.height(12.dp))
            Text(appName, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row {
                BarChart(
                    values = weeklyTotals,
                    color = barColor,
                    modifier = Modifier.weight(1f).height(40.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "this week: ${formatDuration(weeklyTotals.lastOrNull() ?: 0L)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/** See #35 - only shown once a parent has turned unlock tracking on for this profile. */
@Composable
private fun UnlocksSection(report: WeeklyReport) {
    val chronological = report.weeks.reversed()
    val thisWeek = report.weeks.firstOrNull()
    Column(Modifier.padding(16.dp)) {
        Text("Unlocks", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Weekly total: ${thisWeek?.unlockCount ?: 0} this week",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        BarChart(
            values = chronological.map { it.unlockCount.toLong() },
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.fillMaxWidth().height(80.dp)
        )
        WeekLabelsRow(chronological)
        Spacer(Modifier.height(12.dp))
        Text("Opened first after unlocking", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Top apps over the last $REPORT_WEEKS weeks, oldest to newest.",
            style = MaterialTheme.typography.bodySmall
        )
        AppCountTrendList(
            trends = topAppCountTrends(report, TOP_APPS_SHOWN) { it.unlockFirstApps },
            emptyText = "Nothing recorded yet - this fills in once the phone has synced after tracking was turned on."
        )
    }
}

/** See #35 - only shown once a parent has turned notification tracking on for this profile. */
@Composable
private fun NotificationsSection(report: WeeklyReport) {
    val chronological = report.weeks.reversed()
    val thisWeek = report.weeks.firstOrNull()
    Column(Modifier.padding(16.dp)) {
        Text("Notifications", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Weekly total: ${thisWeek?.notificationCount ?: 0} this week",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        BarChart(
            values = chronological.map { it.notificationCount.toLong() },
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.fillMaxWidth().height(80.dp)
        )
        WeekLabelsRow(chronological)
        Spacer(Modifier.height(12.dp))
        Text("By app", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Top apps over the last $REPORT_WEEKS weeks, oldest to newest.",
            style = MaterialTheme.typography.bodySmall
        )
        AppCountTrendList(
            trends = topAppCountTrends(report, TOP_APPS_SHOWN) { it.notificationsByApp },
            emptyText = "Nothing recorded yet - this fills in once the phone has synced after tracking was turned on."
        )
    }
}

/** One line per day with synced data this week, newest first - the "daily digest." */
@Composable
private fun DailyDigestSection(report: WeeklyReport, showUnlocks: Boolean, showNotifications: Boolean) {
    val days = report.weeks.firstOrNull()?.daily.orEmpty()
    Column(Modifier.padding(16.dp)) {
        Text("Daily digest", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        if (days.isEmpty()) {
            Text("No days synced yet this week.", style = MaterialTheme.typography.bodySmall)
        }
        days.forEach { day ->
            val parts = buildList {
                if (showUnlocks) add("${day.unlockCount} unlocks")
                if (showNotifications) add("${day.notificationCount} notifications")
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(formatDayLabel(day.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WeekLabelsRow(chronological: List<WeekSummary>) {
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth()) {
        chronological.forEach { week ->
            Text(
                formatWeekLabel(week),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** One row per app: its name, a small bar per week (oldest to newest), and this week's count. */
@Composable
private fun AppCountTrendList(trends: List<AppCountTrend>, emptyText: String) {
    if (trends.isEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(emptyText, style = MaterialTheme.typography.bodySmall)
        return
    }
    val barColor = MaterialTheme.colorScheme.secondary
    trends.forEach { app ->
        Spacer(Modifier.height(12.dp))
        Text(app.appName, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Row {
            BarChart(
                values = app.weeklyCounts.map { it.toLong() },
                color = barColor,
                modifier = Modifier.weight(1f).height(40.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("this week: ${app.thisWeek}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatDayLabel(date: String): String {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
    return parsed?.let { SimpleDateFormat("EEE, MMM d", Locale.US).format(it) } ?: date
}

/** Simple equal-width bars, tallest = the largest [values] entry. Left to right = [values] order. */
@Composable
private fun BarChart(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    val maxValue = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier = modifier) {
        drawBars(values, maxValue, color)
    }
}

private fun DrawScope.drawBars(values: List<Long>, maxValue: Long, color: Color) {
    if (values.isEmpty()) return
    val gap = 8.dp.toPx()
    val barWidth = (size.width - gap * (values.size - 1)) / values.size
    values.forEachIndexed { index, value ->
        val barHeight = size.height * (value.toFloat() / maxValue)
        val x = index * (barWidth + gap)
        drawRoundRect(
            color = color,
            topLeft = Offset(x, size.height - barHeight),
            size = Size(barWidth.coerceAtLeast(0f), barHeight),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
    }
}

private fun formatWeekLabel(week: WeekSummary): String {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val formatter = SimpleDateFormat("MMM d", Locale.US)
    val start = parser.parse(week.weekStartDate)?.let(formatter::format) ?: week.weekStartDate
    return start
}

/** Packages ranked by total time summed across every week in [report], most-used first. */
private fun topAppsAcrossWeeks(report: WeeklyReport, limit: Int): List<Pair<String, String>> {
    val totals = LinkedHashMap<String, Pair<String, Long>>()
    for (week in report.weeks) {
        for (app in week.byApp) {
            val existing = totals[app.packageName]
            totals[app.packageName] = app.appName to ((existing?.second ?: 0L) + app.totalMs)
        }
    }
    return totals.entries
        .sortedByDescending { it.value.second }
        .take(limit)
        .map { it.key to it.value.first }
}
