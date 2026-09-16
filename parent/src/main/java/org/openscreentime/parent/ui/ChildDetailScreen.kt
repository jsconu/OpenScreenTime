package org.openscreentime.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.computeStreak
import org.openscreentime.shared.model.formatMinutesOfDay
import org.openscreentime.shared.model.todayDateString
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.sharedui.BedtimeWindowDialog
import org.openscreentime.sharedui.MinutesInputDialog
import org.openscreentime.sharedui.UnlockGoalInputDialog

private const val STREAK_LOOKBACK_DAYS = 14

@Composable
fun ChildDetailScreen(
    repository: FamilyRepository,
    childId: String,
    onBack: () -> Unit
) {
    val parentUid = repository.currentUid ?: return
    val scope = rememberCoroutineScope()

    var child by remember { mutableStateOf<ChildProfile?>(null) }
    var stats by remember { mutableStateOf(DailyStats(date = todayDateString())) }
    var streakDays by remember { mutableIntStateOf(0) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var showUnlockGoalDialog by remember { mutableStateOf(false) }
    var showBedtimeDialog by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<String?>(null) }
    var showLockConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    DisposableEffect(childId) {
        val reg1 = repository.listenChildren(parentUid) { list ->
            child = list.firstOrNull { it.id == childId }
        }
        val reg2 = repository.listenDailyStats(parentUid, childId, todayDateString()) { stats = it }
        onDispose {
            reg1.remove()
            reg2.remove()
        }
    }

    val currentChild = child ?: return

    LaunchedEffect(childId, currentChild.dailyLimitMinutes, currentChild.dailyUnlockGoal) {
        val recent = repository.getRecentDailyStats(parentUid, childId, STREAK_LOOKBACK_DAYS)
        streakDays = computeStreak(recent, currentChild.dailyLimitMinutes, currentChild.dailyUnlockGoal)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentChild.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            val hasPendingProposal = currentChild.proposedDailyLimitMinutes != null || currentChild.proposedAppLimits != null
            if (hasPendingProposal) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${currentChild.name} suggested a change", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            currentChild.proposedDailyLimitMinutes?.let {
                                Text(
                                    "New daily limit: $it min (currently ${currentChild.dailyLimitMinutes} min)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            currentChild.proposedAppLimits?.let {
                                Spacer(Modifier.height(4.dp))
                                Text("Suggested app limits included", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch { repository.approveProposal(parentUid, childId, currentChild) }
                                }) { Text("Approve") }
                                OutlinedButton(onClick = {
                                    scope.launch { repository.declineProposal(parentUid, childId) }
                                }) { Text("Decline") }
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            if (currentChild.locked) {
                                scope.launch { repository.setLocked(parentUid, childId, false) }
                            } else {
                                showLockConfirm = true
                            }
                        },
                        colors = if (!currentChild.locked) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (currentChild.locked) "Resume screen time" else "End screen time now")
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Today", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        StatBlock("Screen time", formatDuration(stats.totalScreenTimeMs))
                        StatBlock("Unlocks", stats.unlockCount.toString())
                    }
                    if (streakDays > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "$streakDays day${if (streakDays == 1) "" else "s"} in a row under goal",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    val progress = (stats.totalScreenTimeMs / 60000f) / currentChild.dailyLimitMinutes.coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Daily limit: ${currentChild.dailyLimitMinutes} min", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showLimitDialog = true }) { Text("Change daily limit") }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        currentChild.dailyUnlockGoal?.let { "Unlock goal: $it a day" } ?: "No unlock goal set",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Informational only - never blocks. Today's unlocks: ${stats.unlockCount}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showUnlockGoalDialog = true }) { Text("Change unlock goal") }
                    Spacer(Modifier.height(16.dp))
                    val bedtimeStart = currentChild.bedtimeStartMinutes
                    val bedtimeEnd = currentChild.bedtimeEndMinutes
                    Text(
                        if (bedtimeStart != null && bedtimeEnd != null) {
                            "Bedtime: ${formatMinutesOfDay(bedtimeStart)} - ${formatMinutesOfDay(bedtimeEnd)}"
                        } else {
                            "No bedtime set"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Blocks every app during this window, independent of the daily limit.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showBedtimeDialog = true }) { Text("Change bedtime") }
                }
            }
            item {
                Text(
                    "App usage today",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (stats.appUsage.isEmpty()) {
                item {
                    Text(
                        "No app usage synced yet.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(stats.appUsage.sortedByDescending { it.foregroundTimeMs }, key = { it.packageName }) { app ->
                ListItem(
                    headlineContent = { Text(app.appName) },
                    supportingContent = {
                        val limit = currentChild.appLimits[app.packageName]
                        Text(
                            if (limit != null) {
                                "${formatDuration(app.foregroundTimeMs)} of ${limit}m limit"
                            } else {
                                formatDuration(app.foregroundTimeMs)
                            }
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { editingApp = app.packageName }) { Text("Limit") }
                    }
                )
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove ${currentChild.name}")
                    }
                }
            }
        }
    }

    if (showLimitDialog) {
        MinutesInputDialog(
            title = "Daily screen time limit",
            initialMinutes = currentChild.dailyLimitMinutes,
            onDismiss = { showLimitDialog = false },
            onConfirm = { minutes ->
                scope.launch { repository.updateDailyLimit(parentUid, childId, minutes) }
                showLimitDialog = false
            }
        )
    }

    if (showUnlockGoalDialog) {
        UnlockGoalInputDialog(
            initialGoal = currentChild.dailyUnlockGoal,
            onDismiss = { showUnlockGoalDialog = false },
            onConfirm = { goal ->
                scope.launch { repository.updateDailyUnlockGoal(parentUid, childId, goal) }
                showUnlockGoalDialog = false
            }
        )
    }

    if (showBedtimeDialog) {
        BedtimeWindowDialog(
            initialStartMinutes = currentChild.bedtimeStartMinutes,
            initialEndMinutes = currentChild.bedtimeEndMinutes,
            onDismiss = { showBedtimeDialog = false },
            onConfirm = { start, end ->
                scope.launch { repository.updateBedtimeWindow(parentUid, childId, start, end) }
                showBedtimeDialog = false
            }
        )
    }

    if (showLockConfirm) {
        AlertDialog(
            onDismissRequest = { showLockConfirm = false },
            title = { Text("End screen time now?") },
            text = { Text("This blocks every app on ${currentChild.name}'s device until you resume it.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.setLocked(parentUid, childId, true) }
                    showLockConfirm = false
                }) { Text("End now") }
            },
            dismissButton = { TextButton(onClick = { showLockConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove ${currentChild.name}?") },
            text = {
                Text(
                    "This deletes ${currentChild.name}'s profile and all of their screen time history. " +
                        "The kid app will need to be unpaired and re-paired to track again. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteChild(parentUid, childId)
                        onBack()
                    }
                    showDeleteConfirm = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    editingApp?.let { pkg ->
        val appName = stats.appUsage.firstOrNull { it.packageName == pkg }?.appName ?: pkg
        MinutesInputDialog(
            title = "Daily limit for $appName",
            initialMinutes = currentChild.appLimits[pkg] ?: 60,
            onDismiss = { editingApp = null },
            onConfirm = { minutes ->
                scope.launch {
                    val updated = currentChild.appLimits.toMutableMap().apply { put(pkg, minutes) }
                    repository.updateAppLimits(parentUid, childId, updated)
                }
                editingApp = null
            }
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

