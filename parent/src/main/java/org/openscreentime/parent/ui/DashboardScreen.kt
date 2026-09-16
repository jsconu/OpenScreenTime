package org.openscreentime.parent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openscreentime.parent.data.TipsStore
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.computeStreak
import org.openscreentime.shared.model.currentDayIndex
import org.openscreentime.shared.model.currentDayParentTip
import org.openscreentime.shared.model.todayDateString
import org.openscreentime.shared.repo.FamilyRepository

private const val STREAK_LOOKBACK_DAYS = 14

@Composable
fun DashboardScreen(
    repository: FamilyRepository,
    onOpenChild: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSelfTracking: () -> Unit,
    onSendFeedback: () -> Unit,
    onSignOut: () -> Unit
) {
    val parentUid = repository.currentUid ?: return
    val scope = rememberCoroutineScope()
    var children by remember { mutableStateOf<List<ChildProfile>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newChildCode by remember { mutableStateOf<String?>(null) }

    DisposableEffect(parentUid) {
        // Excludes the parent's own self-tracking profile (see #8) - that one gets its
        // own dedicated "My screen time" card above, not a slot in the kids list.
        val reg = repository.listenChildren(parentUid) { children = it.filter { child -> !child.isSelf } }
        onDispose { reg.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your children") },
                actions = {
                    TextButton(onClick = onOpenAppearance) { Text("Style") }
                    TextButton(onClick = onOpenSettings) { Text("Passcode") }
                    TextButton(onClick = onSendFeedback) { Text("Feedback") }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .testTag("dashboard_add_child")
                    .semantics { contentDescription = "Add a child" }
            ) { Text("+") }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                TipOfTheDayCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable(onClick = onOpenSelfTracking)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("My screen time", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Track and limit your own screen time on this device too.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (children.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Add your first child to get started.")
                    }
                }
            } else {
                items(children, key = { it.id }) { child ->
                    ChildSummaryCard(
                        repository = repository,
                        parentUid = parentUid,
                        child = child,
                        onClick = { onOpenChild(child.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddChildDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { name ->
                scope.launch {
                    val child = repository.createChild(parentUid, name)
                    newChildCode = child.pairingCode
                    showAddDialog = false
                }
            }
        )
    }

    newChildCode?.let { code ->
        AlertDialog(
            onDismissRequest = { newChildCode = null },
            title = { Text("Pairing code") },
            text = {
                DialogTestTagRoot {
                    Column {
                        Text("Enter this code in OpenScreenTime Kid on your child's phone:")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            code,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.testTag("pairing_code_value")
                        )
                    }
                }
            },
            confirmButton = {
                DialogTestTagRoot {
                    TextButton(
                        onClick = { newChildCode = null },
                        modifier = Modifier.testTag("pairing_code_done")
                    ) { Text("Done") }
                }
            }
        )
    }
}

/**
 * AlertDialog content composes into its own Window, disconnected from the
 * activity's semantics tree - so testTagsAsResourceId (set once at the
 * activity root in MainActivity) doesn't reach inside a dialog on its own.
 * Wrap any dialog slot that has testTag'd children in this so UiAutomator
 * (used by the :e2e module) can still match them by resource-id.
 */
@Composable
private fun DialogTestTagRoot(content: @Composable () -> Unit) {
    Box(modifier = Modifier.semantics { testTagsAsResourceId = true }) {
        content()
    }
}

@Composable
private fun ChildSummaryCard(
    repository: FamilyRepository,
    parentUid: String,
    child: ChildProfile,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(DailyStats(date = todayDateString())) }
    var streakDays by remember { mutableIntStateOf(0) }
    var showLockConfirm by remember { mutableStateOf(false) }

    DisposableEffect(child.id) {
        val reg = repository.listenDailyStats(parentUid, child.id, todayDateString()) { stats = it }
        onDispose { reg.remove() }
    }

    LaunchedEffect(child.id, child.dailyLimitMinutes, child.dailyUnlockGoal) {
        val recent = repository.getRecentDailyStats(parentUid, child.id, STREAK_LOOKBACK_DAYS)
        streakDays = computeStreak(recent, child.dailyLimitMinutes, child.dailyUnlockGoal)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(child.name, style = MaterialTheme.typography.titleMedium)
                if (child.paired) {
                    OutlinedButton(
                        onClick = {
                            if (child.locked) {
                                scope.launch { repository.setLocked(parentUid, child.id, false) }
                            } else {
                                showLockConfirm = true
                            }
                        },
                        colors = if (child.locked) {
                            ButtonDefaults.outlinedButtonColors()
                        } else {
                            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        },
                        // Only rendered once child.paired is true - a reliable "is paired" marker for tests.
                        modifier = Modifier.testTag("dashboard_child_lock_toggle")
                    ) {
                        Text(if (child.locked) "Resume" else "Lock now")
                    }
                }
            }
            if (!child.paired) {
                Spacer(Modifier.height(4.dp))
                Text("Waiting for device pairing (code: ${child.pairingCode})", style = MaterialTheme.typography.bodySmall)
            } else {
                if (child.proposedDailyLimitMinutes != null || child.proposedAppLimits != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${child.name} suggested a change - tap to review",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatColumn("Screen time today", formatDuration(stats.totalScreenTimeMs))
                    StatColumn("Unlocks", stats.unlockCount.toString())
                    StatColumn("Daily limit", "${child.dailyLimitMinutes} min")
                }
                if (streakDays > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$streakDays day${if (streakDays == 1) "" else "s"} in a row under goal",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (child.locked) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Screen time is paused",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showLockConfirm) {
        AlertDialog(
            onDismissRequest = { showLockConfirm = false },
            title = { Text("End screen time now?") },
            text = { Text("This blocks every app on ${child.name}'s device until you resume it.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.setLocked(parentUid, child.id, true) }
                    showLockConfirm = false
                }) { Text("Lock now") }
            },
            dismissButton = { TextButton(onClick = { showLockConfirm = false }) { Text("Cancel") } }
        )
    }
}

/**
 * A single tip for the day, not a fresh one per app open - see #17. Checking it off just
 * shows a one-line "Nice job!" in place of the checkbox label; nothing is counted or
 * remembered beyond "was *today's* tip checked" - when the day turns over, a new tip
 * appears already unchecked, with no record of whether the previous day's was ever
 * checked off. That's deliberate, not a gap: this is acknowledgment-only, the same
 * non-punitive rule as the streaks feature (#13) - there is no way for this to ever show
 * "you missed a day."
 */
@Composable
private fun TipOfTheDayCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tipsStore = remember { TipsStore(context) }
    val dayIndex = remember { currentDayIndex() }
    var acknowledged by remember { mutableStateOf(tipsStore.acknowledgedDayIndex == dayIndex) }

    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text("Today's idea", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(2.dp))
            Text(currentDayParentTip(), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = acknowledged,
                    onCheckedChange = { checked ->
                        acknowledged = checked
                        tipsStore.acknowledgedDayIndex = if (checked) dayIndex else null
                    }
                )
                Text(
                    if (acknowledged) "Nice job! You're practicing great screen time management!" else "I did this",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AddChildDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a child") },
        text = {
            DialogTestTagRoot {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Child's name") },
                    singleLine = true,
                    modifier = Modifier.testTag("add_child_name")
                )
            }
        },
        confirmButton = {
            DialogTestTagRoot {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onCreate(name) },
                    modifier = Modifier.testTag("add_child_create")
                ) { Text("Create") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
