package org.openscreentime.kid.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.shared.model.AppUsage
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.formatMinutesOfDay
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.sharedui.BedtimeWindowDialog
import org.openscreentime.sharedui.MinutesInputDialog
import org.openscreentime.sharedui.UnlockGoalInputDialog

/**
 * Shown after a correct passcode entry in [ParentModeUnlockScreen]. Mirrors the limit
 * editors in the parent app's ChildDetailScreen, but reads today's app usage from this
 * device's local [UsageStore] rather than round-tripping through Firestore.
 */
@Composable
fun ParentControlsScreen(
    repository: FamilyRepository,
    parentUid: String,
    childId: String,
    child: ChildProfile,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val usageStore = remember { UsageStore(context) }

    val appUsage = remember {
        usageStore.appUsageMs.map { (pkg, ms) ->
            AppUsage(packageName = pkg, appName = usageStore.appNames[pkg] ?: pkg, foregroundTimeMs = ms)
        }.sortedByDescending { it.foregroundTimeMs }
    }

    var showLimitDialog by remember { mutableStateOf(false) }
    var showUnlockGoalDialog by remember { mutableStateOf(false) }
    var showBedtimeDialog by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<String?>(null) }
    var showLockConfirm by remember { mutableStateOf(false) }
    var newBlockedDomain by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parent controls") },
                navigationIcon = { TextButton(onClick = onDone) { Text("Done") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            if (child.locked) {
                                scope.launch { repository.setLocked(parentUid, childId, false) }
                            } else {
                                showLockConfirm = true
                            }
                        },
                        colors = if (!child.locked) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (child.locked) "Resume screen time" else "End screen time now")
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Daily limit: ${child.dailyLimitMinutes} min", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showLimitDialog = true }) { Text("Change daily limit") }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        child.dailyUnlockGoal?.let { "Unlock goal: $it a day" } ?: "No unlock goal set",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showUnlockGoalDialog = true }) { Text("Change unlock goal") }
                    Spacer(Modifier.height(16.dp))
                    val bedtimeStart = child.bedtimeStartMinutes
                    val bedtimeEnd = child.bedtimeEndMinutes
                    Text(
                        if (bedtimeStart != null && bedtimeEnd != null) {
                            "Bedtime: ${formatMinutesOfDay(bedtimeStart)} - ${formatMinutesOfDay(bedtimeEnd)}"
                        } else {
                            "No bedtime set"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showBedtimeDialog = true }) { Text("Change bedtime") }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("Blocked websites", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Blocks a domain and its subdomains in any browser on this device.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newBlockedDomain,
                            onValueChange = { newBlockedDomain = it },
                            label = { Text("e.g. tiktok.com") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = newBlockedDomain.isNotBlank(),
                            onClick = {
                                val domain = newBlockedDomain.trim().trimEnd('.').lowercase()
                                if (domain.isNotEmpty() && domain !in child.blockedDomains) {
                                    scope.launch {
                                        repository.updateBlockedDomains(parentUid, childId, child.blockedDomains + domain)
                                    }
                                }
                                newBlockedDomain = ""
                            }
                        ) { Text("Block") }
                    }
                }
            }
            if (child.blockedDomains.isEmpty()) {
                item {
                    Text(
                        "No websites blocked.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(child.blockedDomains.sorted(), key = { it }) { domain ->
                ListItem(
                    headlineContent = { Text(domain) },
                    trailingContent = {
                        TextButton(onClick = {
                            scope.launch {
                                repository.updateBlockedDomains(parentUid, childId, child.blockedDomains - domain)
                            }
                        }) { Text("Remove") }
                    }
                )
            }
            item {
                Text(
                    "App limits",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (appUsage.isEmpty()) {
                item {
                    Text(
                        "No app usage recorded yet today.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(appUsage, key = { it.packageName }) { app ->
                ListItem(
                    headlineContent = { Text(app.appName) },
                    supportingContent = {
                        val limit = child.appLimits[app.packageName]
                        Text(if (limit != null) "Limit: $limit min/day" else "No limit set")
                    },
                    trailingContent = {
                        TextButton(onClick = { editingApp = app.packageName }) { Text("Limit") }
                    }
                )
            }
        }
    }

    if (showBedtimeDialog) {
        BedtimeWindowDialog(
            initialStartMinutes = child.bedtimeStartMinutes,
            initialEndMinutes = child.bedtimeEndMinutes,
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
            text = { Text("This blocks every app on this device until a parent resumes it.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.setLocked(parentUid, childId, true) }
                    showLockConfirm = false
                }) { Text("End now") }
            },
            dismissButton = { TextButton(onClick = { showLockConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showLimitDialog) {
        MinutesInputDialog(
            title = "Daily screen time limit",
            initialMinutes = child.dailyLimitMinutes,
            onDismiss = { showLimitDialog = false },
            onConfirm = { minutes ->
                scope.launch { repository.updateDailyLimit(parentUid, childId, minutes) }
                showLimitDialog = false
            }
        )
    }

    if (showUnlockGoalDialog) {
        UnlockGoalInputDialog(
            initialGoal = child.dailyUnlockGoal,
            onDismiss = { showUnlockGoalDialog = false },
            onConfirm = { goal ->
                scope.launch { repository.updateDailyUnlockGoal(parentUid, childId, goal) }
                showUnlockGoalDialog = false
            }
        )
    }

    editingApp?.let { pkg ->
        val appName = usageStore.appNames[pkg] ?: pkg
        MinutesInputDialog(
            title = "Daily limit for $appName",
            initialMinutes = child.appLimits[pkg] ?: 60,
            onDismiss = { editingApp = null },
            onConfirm = { minutes ->
                scope.launch {
                    val updated = child.appLimits.toMutableMap().apply { put(pkg, minutes) }
                    repository.updateAppLimits(parentUid, childId, updated)
                }
                editingApp = null
            }
        )
    }
}

