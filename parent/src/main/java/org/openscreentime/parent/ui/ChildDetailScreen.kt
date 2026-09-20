package org.openscreentime.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openscreentime.parent.data.ReportOpenTracker
import org.openscreentime.shared.model.AppUsage
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.InstalledApp
import org.openscreentime.shared.model.TRACKING_DISPLAY_NOTE
import org.openscreentime.shared.model.TrackingToggle
import org.openscreentime.shared.model.addBlockedDomain
import org.openscreentime.shared.model.computeStreak
import org.openscreentime.shared.model.formatDuration
import org.openscreentime.shared.model.removeBlockedDomain
import org.openscreentime.shared.model.AppCount
import org.openscreentime.shared.model.AppSort
import org.openscreentime.shared.model.describeBedtimeWindow
import org.openscreentime.shared.model.sortApps
import org.openscreentime.shared.model.mergeUsageWithInstalled
import org.openscreentime.shared.util.listLaunchableApps
import org.openscreentime.shared.model.todayDateString
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.sharedui.trackingSection
import org.openscreentime.sharedui.AppSortToggle
import org.openscreentime.sharedui.BedtimeWindowDialog
import org.openscreentime.sharedui.MinutesInputDialog
import org.openscreentime.sharedui.UnlockGoalInputDialog

private const val STREAK_LOOKBACK_DAYS = 14
/** See #30 - opening the detailed report this many times already today prompts an "are you sure." */
private const val FREQUENT_CHECK_THRESHOLD = 3

/**
 * Broken into named sections (mirroring the iOS ChildDetailView's LockSection/TodaySection/
 * AppUsageSection split) rather than one large LazyColumn body - each section owns its own
 * slice of the child/stats state and the callbacks it needs, so a change to (say) the bedtime
 * copy doesn't require rereading the app-usage list logic next to it.
 */
@Composable
fun ChildDetailScreen(
    repository: FamilyRepository,
    childId: String,
    onOpenReport: () -> Unit,
    onBack: () -> Unit,
    /** Only for the parent's own "Me" profile: opens the permissions screen. */
    onOpenPermissions: (() -> Unit)? = null,
    /** True while a permission this device needs for tracking is still missing (self profile only). */
    permissionsMissing: Boolean = false
) {
    val parentUid = repository.currentUid ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val reportTracker = remember { ReportOpenTracker(context) }

    var child by remember { mutableStateOf<ChildProfile?>(null) }
    var stats by remember { mutableStateOf(DailyStats(date = todayDateString())) }
    var streakDays by remember { mutableIntStateOf(0) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var showUnlockGoalDialog by remember { mutableStateOf(false) }
    var showBedtimeDialog by remember { mutableStateOf(false) }
    var showFrequentCheckWarning by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<String?>(null) }
    var showLockConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newBlockedDomain by remember { mutableStateOf("") }
    var kidInstalledApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var appSort by rememberSaveable { mutableStateOf(AppSort.USAGE) }

    DisposableEffect(childId) {
        val reg1 = repository.listenChildren(parentUid) { list ->
            child = list.firstOrNull { it.id == childId }
        }
        val reg2 = repository.listenDailyStats(parentUid, childId, todayDateString()) { stats = it }
        // What the kid's phone last reported as installed (empty until it has synced once).
        val reg3 = repository.listenInstalledApps(parentUid, childId) { kidInstalledApps = it }
        onDispose {
            reg1.remove()
            reg2.remove()
            reg3.remove()
        }
    }

    val currentChild = child ?: return

    // Every app on the device, not only ones already used today. On the parent's own "Me" profile this
    // phone is the device; for a paired kid it's the list their phone last published (see
    // SyncWorker), which is empty until that phone has synced once.
    val displayedApps = remember(currentChild.isSelf, stats.appUsage, kidInstalledApps, appSort) {
        sortApps(
            mergeUsageWithInstalled(
                stats.appUsage,
                if (currentChild.isSelf) listLaunchableApps(context) else kidInstalledApps
            ),
            appSort
        )
    }

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
            if (currentChild.isSelf && onOpenPermissions != null) {
                item {
                    Card(
                        colors = if (permissionsMissing) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        } else {
                            CardDefaults.cardColors()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Permissions", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (permissionsMissing) {
                                    "A few permissions are still needed on this phone for tracking to work."
                                } else {
                                    "Everything tracking needs is turned on. You can stop tracking here too."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onOpenPermissions,
                                modifier = Modifier.fillMaxWidth().testTag("self_open_permissions")
                            ) { Text(if (permissionsMissing) "Fix permissions" else "Permissions") }
                        }
                    }
                }
            }
            pendingProposalSection(
                child = currentChild,
                onApprove = { scope.launch { repository.approveProposal(parentUid, childId, currentChild) } },
                onDecline = { scope.launch { repository.declineProposal(parentUid, childId) } }
            )
            extraTimeRequestSection(
                child = currentChild,
                onGrant = { minutes -> scope.launch { repository.grantExtraTime(parentUid, childId, minutes) } },
                onDecline = { scope.launch { repository.declineExtraTimeRequest(parentUid, childId) } }
            )
            lockAndLimitsSection(
                child = currentChild,
                stats = stats,
                streakDays = streakDays,
                onToggleLock = { scope.launch { repository.setLocked(parentUid, childId, false) } },
                onRequestLockConfirm = { showLockConfirm = true },
                onChangeLimit = { showLimitDialog = true },
                onChangeUnlockGoal = { showUnlockGoalDialog = true },
                onChangeBedtime = { showBedtimeDialog = true }
            )
            weeklyReportSection(
                onOpenReport = {
                    if (reportTracker.todayOpenCount >= FREQUENT_CHECK_THRESHOLD) {
                        showFrequentCheckWarning = true
                    } else {
                        reportTracker.recordOpen()
                        onOpenReport()
                    }
                }
            )
            trackingSection(
                child = currentChild,
                onToggle = { toggle, enabled ->
                    scope.launch { repository.setTrackingToggle(parentUid, childId, toggle, enabled) }
                }
            )
            websiteBlockingSection(
                blockedDomains = currentChild.blockedDomains,
                newDomainText = newBlockedDomain,
                onNewDomainTextChange = { newBlockedDomain = it },
                onAddDomain = {
                    val updated = addBlockedDomain(currentChild.blockedDomains, newBlockedDomain)
                    if (updated != currentChild.blockedDomains) {
                        scope.launch { repository.addBlockedDomain(parentUid, childId, updated.last()) }
                    }
                    newBlockedDomain = ""
                },
                onRemoveDomain = { domain ->
                    scope.launch { repository.removeBlockedDomain(parentUid, childId, domain) }
                }
            )
            if (currentChild.trackWebsites) {
                websiteActivitySection(stats.websiteCounts)
            }
            appUsageSection(
                sort = appSort,
                onSortChange = { appSort = it },
                appUsage = displayedApps,
                appLimits = currentChild.appLimits,
                alwaysAllowedPackages = currentChild.alwaysAllowedPackages,
                onEditApp = { editingApp = it },
                onToggleAlwaysAllowed = { pkg, allowed ->
                    scope.launch { repository.setAlwaysAllowedPackage(parentUid, childId, pkg, allowed) }
                }
            )
            removeChildSection(childName = currentChild.name, onRequestDelete = { showDeleteConfirm = true })
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

    if (showFrequentCheckWarning) {
        AlertDialog(
            onDismissRequest = { showFrequentCheckWarning = false },
            title = { Text("Check in, not check up") },
            text = {
                Text(
                    "You've opened a report a few times today already. A quick look now and " +
                        "then makes sense, but checking constantly can turn this into its own " +
                        "source of stress. Still want to open it?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    reportTracker.recordOpen()
                    showFrequentCheckWarning = false
                    onOpenReport()
                }) { Text("Open anyway") }
            },
            dismissButton = { TextButton(onClick = { showFrequentCheckWarning = false }) { Text("Not now") } }
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
        val appName = displayedApps.firstOrNull { it.packageName == pkg }?.appName ?: pkg
        MinutesInputDialog(
            title = "Daily limit for $appName",
            initialMinutes = currentChild.appLimits[pkg] ?: 60,
            onDismiss = { editingApp = null },
            onConfirm = { minutes ->
                scope.launch { repository.setAppLimit(parentUid, childId, pkg, minutes) }
                editingApp = null
            }
        )
    }
}

private fun LazyListScope.pendingProposalSection(
    child: ChildProfile,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    val hasPendingProposal = child.proposedDailyLimitMinutes != null || child.proposedAppLimits != null
    if (!hasPendingProposal) return
    item {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("${child.name} suggested a change", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                child.proposedDailyLimitMinutes?.let {
                    Text(
                        "New daily limit: $it min (currently ${child.dailyLimitMinutes} min)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                child.proposedAppLimits?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Suggested app limits included", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApprove) { Text("Approve") }
                    OutlinedButton(onClick = onDecline) { Text("Decline") }
                }
            }
        }
    }
}

/** See #23 - a kid-requested "more time" extension, awaiting a Grant or Decline. */
private fun LazyListScope.extraTimeRequestSection(
    child: ChildProfile,
    onGrant: (Int) -> Unit,
    onDecline: () -> Unit
) {
    val requested = child.requestedExtraMinutes ?: return
    item {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("${child.name} is asking for more time", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Requested: $requested more minutes", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onGrant(requested) }) { Text("Grant $requested min") }
                    OutlinedButton(onClick = onDecline) { Text("Decline") }
                }
            }
        }
    }
}

private fun LazyListScope.lockAndLimitsSection(
    child: ChildProfile,
    stats: DailyStats,
    streakDays: Int,
    onToggleLock: () -> Unit,
    onRequestLockConfirm: () -> Unit,
    onChangeLimit: () -> Unit,
    onChangeUnlockGoal: () -> Unit,
    onChangeBedtime: () -> Unit
) {
    item {
        Column(Modifier.padding(16.dp)) {
            Button(
                onClick = { if (child.locked) onToggleLock() else onRequestLockConfirm() },
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
            val progress = (stats.totalScreenTimeMs / 60000f) / child.dailyLimitMinutes.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text("Daily limit: ${child.dailyLimitMinutes} min", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onChangeLimit) { Text("Change daily limit") }
            Spacer(Modifier.height(16.dp))
            Text(
                child.dailyUnlockGoal?.let { "Unlock goal: $it a day" } ?: "No unlock goal set",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Informational only - never blocks. Today's unlocks: ${stats.unlockCount}.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onChangeUnlockGoal) { Text("Change unlock goal") }
            Spacer(Modifier.height(16.dp))
            val bedtimeStart = child.bedtimeStartMinutes
            val bedtimeEnd = child.bedtimeEndMinutes
            Text(
                if (bedtimeStart != null && bedtimeEnd != null) {
                    "Bedtime: ${describeBedtimeWindow(bedtimeStart, bedtimeEnd)}"
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
            OutlinedButton(onClick = onChangeBedtime) { Text("Change bedtime") }
        }
    }
}

/**
 * A single tap through to the rolling 4-week report (see #29) - deliberately not shown
 * inline here, the same "one tap away, not glanceable" reasoning as the Dashboard's calm
 * status card instead of raw numbers.
 */
private fun LazyListScope.weeklyReportSection(onOpenReport: () -> Unit) {
    item {
        Column(Modifier.padding(16.dp)) {
            OutlinedButton(onClick = onOpenReport, modifier = Modifier.fillMaxWidth()) {
                Text("View weekly report")
            }
        }
    }
}

/**
 * Domains blocked device-wide, in any browser, via the kid device's local DNS-sinkhole
 * VPN (see #19). Suffix-matched, so one entry covers every subdomain.
 */
private fun LazyListScope.websiteBlockingSection(
    blockedDomains: List<String>,
    newDomainText: String,
    onNewDomainTextChange: (String) -> Unit,
    onAddDomain: () -> Unit,
    onRemoveDomain: (String) -> Unit
) {
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
                    value = newDomainText,
                    onValueChange = onNewDomainTextChange,
                    label = { Text("e.g. tiktok.com") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAddDomain, enabled = newDomainText.isNotBlank()) { Text("Block") }
            }
        }
    }
    if (blockedDomains.isEmpty()) {
        item {
            Text(
                "No websites blocked.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
    items(blockedDomains.sorted(), key = { it }) { domain ->
        ListItem(
            headlineContent = { Text(domain) },
            trailingContent = {
                TextButton(onClick = { onRemoveDomain(domain) }) { Text("Remove") }
            }
        )
    }
}

private fun LazyListScope.appUsageSection(
    sort: AppSort,
    onSortChange: (AppSort) -> Unit,
    appUsage: List<AppUsage>,
    appLimits: Map<String, Int>,
    alwaysAllowedPackages: List<String>,
    onEditApp: (String) -> Unit,
    onToggleAlwaysAllowed: (String, Boolean) -> Unit
) {
    item {
        Text(
            "App usage today",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            "\"Always allow\" lets an app through the daily limit and bedtime, no matter what - " +
                "meant for a phone/calling app or maps, not a way to skip a limit day to day.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        AppSortToggle(sort = sort, onChange = onSortChange, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    if (appUsage.isEmpty()) {
        item {
            Text(
                "No apps to show yet.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
    items(appUsage, key = { it.packageName }) { app ->
        AppUsageRow(
            app = app,
            limitMinutes = appLimits[app.packageName],
            alwaysAllowed = app.packageName in alwaysAllowedPackages,
            onEdit = { onEditApp(app.packageName) },
            onToggleAlwaysAllowed = { onToggleAlwaysAllowed(app.packageName, it) }
        )
    }
}

@Composable
private fun AppUsageRow(
    app: AppUsage,
    limitMinutes: Int?,
    alwaysAllowed: Boolean,
    onEdit: () -> Unit,
    onToggleAlwaysAllowed: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(app.appName) },
        supportingContent = {
            Text(
                if (limitMinutes != null) {
                    "${formatDuration(app.foregroundTimeMs)} of ${limitMinutes}m limit"
                } else {
                    formatDuration(app.foregroundTimeMs)
                }
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Always allow", style = MaterialTheme.typography.labelSmall)
                Checkbox(checked = alwaysAllowed, onCheckedChange = onToggleAlwaysAllowed)
                TextButton(onClick = onEdit) { Text("Limit") }
            }
        }
    )
}

private fun LazyListScope.removeChildSection(childName: String, onRequestDelete: () -> Unit) {
    item {
        Column(Modifier.padding(16.dp)) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onRequestDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Remove $childName")
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * See #41 - which sites the device looked up while a browser was open today. Shown only while "Track websites"
 * is on. Site names only: no pages, searches or time, and one entry means "a burst of activity", not a visit
 * count you can multiply into minutes. Anything that uses its own DNS (a browser's Secure DNS, Android's
 * Private DNS) doesn't show up.
 */
private fun LazyListScope.websiteActivitySection(sites: List<AppCount>) {
    item {
        Text(
            "Websites looked up today",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            "Site names only, counted while a browser was open - not pages, searches, or time spent. A bigger " +
                "number means more activity, not a visit count. Anything a browser looks up privately isn't seen.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    if (sites.isEmpty()) {
        item {
            Text(
                "Nothing yet. Sites appear here once the website filter is on for that phone and a browser has been used.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
    items(sites.take(25), key = { it.packageName }) { site ->
        ListItem(
            headlineContent = { Text(site.packageName) },
            trailingContent = { Text("${site.count}", style = MaterialTheme.typography.labelLarge) }
        )
    }
}
