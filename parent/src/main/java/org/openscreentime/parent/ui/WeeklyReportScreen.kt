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
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.WeekSummary
import org.openscreentime.shared.model.WeeklyReport
import org.openscreentime.shared.model.computeWeeklyReport
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
            item { WeeklyTotalsSection(report) }
            item { AppTrendsSection(report) }
        }
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
