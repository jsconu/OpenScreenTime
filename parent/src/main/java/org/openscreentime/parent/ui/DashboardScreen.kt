package org.openscreentime.parent.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import org.openscreentime.parent.R
import org.openscreentime.parent.data.CalmModePrefs
import org.openscreentime.sharedui.mergedRow
import org.openscreentime.sharedui.switchRow
import org.openscreentime.parent.data.NotificationDigestStore
import org.openscreentime.parent.monitor.CalmSummary
import org.openscreentime.parent.util.isNotificationListenerEnabled
import org.openscreentime.sharedui.StatusIconGuide
import org.openscreentime.parent.BuildConfig
import org.openscreentime.parent.data.TipsStore
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.computeStreak
import org.openscreentime.shared.model.formatDuration
import org.openscreentime.shared.model.currentDayIndex
import org.openscreentime.shared.model.currentDayParentTip
import org.openscreentime.shared.model.todayDateString
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.shared.repo.FirestorePaths

private const val STREAK_LOOKBACK_DAYS = 14

@Composable
fun DashboardScreen(
    repository: FamilyRepository,
    onOpenChild: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSelfTracking: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenDigest: () -> Unit,
    onRequestNotificationListener: () -> Unit,
    onSignOut: () -> Unit
) {
    val parentUid = repository.currentUid ?: return
    val scope = rememberCoroutineScope()
    var children by remember { mutableStateOf<List<ChildProfile>>(emptyList()) }
    var selfProfile by remember { mutableStateOf<ChildProfile?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newChildCode by remember { mutableStateOf<String?>(null) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    // Assume a passcode exists until the check says otherwise, so the prompt never flashes up for
    // someone who already has one. Rechecked whenever this screen comes back into view, so it
    // disappears as soon as the passcode has been set.
    var hasPasscode by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, parentUid) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    // Couldn't check (offline etc.) - leave the prompt as it was rather than nagging.
                    runCatching { repository.getParentPasscode(parentUid) }
                        .onSuccess { hasPasscode = it != null }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(parentUid) {
        // Excludes the parent's own self-tracking profile (see #8) - that one gets its
        // own dedicated "My screen time" card above, not a slot in the kids list.
        val reg = repository.listenChildren(parentUid) { children = it.filter { child -> !child.isSelf } }
        // The self doc's snapshot listener still fires even if self-tracking was never
        // started (an empty, default-valued ChildProfile with isSelf = false) - gate on
        // isSelf, not nullability, to tell "never started" from "actively tracking."
        val selfReg = repository.listenChild(parentUid, FirestorePaths.SELF_CHILD_ID) { child ->
            selfProfile = if (child.isSelf) child else null
        }
        onDispose {
            reg.remove()
            selfReg.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Your Screen Time") },
                actions = {
                    TextButton(
                        onClick = onOpenHelp,
                        modifier = Modifier
                            .testTag("dashboard_help")
                            .semantics { contentDescription = "Help" }
                    ) { Text("?") }
                    var showMenu by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.semantics { contentDescription = "More options" }
                    ) { Text("⋮") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Style") },
                            onClick = { showMenu = false; onOpenAppearance() }
                        )
                        DropdownMenuItem(
                            text = { Text("Passcode") },
                            onClick = { showMenu = false; onOpenSettings() }
                        )
                        DropdownMenuItem(
                            text = { Text("Feedback") },
                            onClick = { showMenu = false; showFeedbackDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = { showMenu = false; onSignOut() }
                        )
                    }
                }
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // First and fixed: nothing above it can appear later (like the passcode prompt below,
            // which shows once its check finishes) and push it out from under a finger.
            item {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("dashboard_add_child")
                ) { Text("Add kid") }
            }
            if (!hasPasscode) {
                item {
                    PasscodePromptCard(
                        onSetPasscode = onOpenSettings,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
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
            item {
                val trackedSelf = selfProfile
                if (trackedSelf != null) {
                    // Reuses the same card every child gets - the self profile is a real
                    // ChildProfile (isSelf = true), so this shows actual numbers directly
                    // on the landing page instead of only a "go manage this" prompt.
                    ChildSummaryCard(
                        repository = repository,
                        parentUid = parentUid,
                        child = trackedSelf,
                        onClick = onOpenSelfTracking,
                        onCustomizeGoals = onOpenSelfTracking
                    )
                } else {
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
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onOpenSelfTracking,
                                modifier = Modifier.fillMaxWidth().testTag("dashboard_self_customize_goals")
                            ) { Text("Customize Screen Time goals") }
                        }
                    }
                }
            }
            item {
                TipOfTheDayCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item {
                StatusGuideCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item {
                NotificationDigestCard(
                    onOpen = onOpenDigest,
                    onRequestListener = onRequestNotificationListener,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
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
        val dialogContext = LocalContext.current
        // Copied the moment the code appears, so it can be pasted straight into the kid app.
        LaunchedEffect(code) { copyPairingCode(dialogContext, code) }
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Copied to your clipboard. It works for 30 minutes.",
                            style = MaterialTheme.typography.bodySmall
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
            },
            dismissButton = {
                TextButton(onClick = { copyPairingCode(dialogContext, code) }) { Text("Copy again") }
            }
        )
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { text, onError ->
                scope.launch {
                    try {
                        repository.submitFeedback(
                            parentUid = parentUid,
                            text = text,
                            appVersion = BuildConfig.VERSION_NAME,
                            device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
                        )
                        showFeedbackDialog = false
                    } catch (e: Exception) {
                        onError()
                    }
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
    onClick: () -> Unit,
    /** Only passed for the parent's own "Me" tile - a shortcut to its goals. */
    onCustomizeGoals: (() -> Unit)? = null
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
                val cardContext = LocalContext.current
                Text("Waiting for device pairing (code: ${child.pairingCode})", style = MaterialTheme.typography.bodySmall)
                Text("A code works for 30 minutes.", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { copyPairingCode(cardContext, child.pairingCode) }) { Text("Copy code") }
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { repository.regeneratePairingCode(parentUid, child.id) }
                                    .onSuccess { copyPairingCode(cardContext, it) }
                            }
                        },
                        modifier = Modifier.testTag("dashboard_new_pairing_code")
                    ) { Text("New code") }
                }
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
                if (onCustomizeGoals != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCustomizeGoals,
                        modifier = Modifier.fillMaxWidth().testTag("dashboard_self_customize_goals")
                    ) { Text("Customize Screen Time goals") }
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

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Off-screen idea",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                currentDayParentTip(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.mergedRow()) {
                Checkbox(
                    checked = acknowledged,
                    onCheckedChange = { checked ->
                        acknowledged = checked
                        tipsStore.acknowledgedDayIndex = if (checked) dayIndex else null
                    }
                )
                Text(
                    if (acknowledged) "Nice job! You're practicing great screen time management!" else "I did this",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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

/**
 * Writes to a write-only Firestore collection rather than opening a mailto: link (see #22) -
 * a mailto: link would show the destination address to every user who taps "Feedback,"
 * defeating the point of keeping it out of the public repo (#21).
 */
@Composable
private fun FeedbackDialog(onDismiss: () -> Unit, onSubmit: (text: String, onError: () -> Unit) -> Unit) {
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send feedback") },
        text = {
            DialogTestTagRoot {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("What's on your mind?") },
                        minLines = 3,
                        modifier = Modifier.testTag("feedback_text")
                    )
                    if (error) {
                        Spacer(Modifier.height(8.dp))
                        Text("Couldn't send - check your connection and try again.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            DialogTestTagRoot {
                TextButton(
                    enabled = text.isNotBlank() && !sending,
                    onClick = {
                        sending = true
                        error = false
                        onSubmit(text) {
                            sending = false
                            error = true
                        }
                    },
                    modifier = Modifier.testTag("feedback_send")
                ) { Text(if (sending) "Sending..." else "Send") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * The same explanation of the calm status icons the kid app shows - where they appear, what thumbs
 * up / open hand / stop mean - from the same shared composable, so both apps say the same thing.
 */
@Composable
private fun StatusGuideCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        StatusIconGuide(
            goodIcon = R.drawable.ic_status_good,
            cautionIcon = R.drawable.ic_status_caution,
            stopIcon = R.drawable.ic_status_stop,
            footnote = "Your kids' phones show these same icons instead of exact numbers, so screen time " +
                "stays something to notice, not something to keep checking - the details are here, " +
                "for you. On this phone the icons appear once you turn on My screen time.",
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * The parent's own calm notification list - the same opt-in, on-device-only list the kid app has.
 * Off until switched on here and notification access is granted in system settings.
 */
@Composable
private fun NotificationDigestCard(
    onOpen: () -> Unit,
    onRequestListener: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { NotificationDigestStore(context) }
    var optedIn by remember { mutableStateOf(store.optedIn) }
    var listenerGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) listenerGranted = isNotificationListenerEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().mergedRow(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Calm notification list", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A plain, read-only list of today's notifications on this phone, grouped by app. " +
                            "It stays on this phone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = optedIn,
                    onCheckedChange = { checked ->
                        optedIn = checked
                        store.optedIn = checked
                        if (checked && !listenerGranted) onRequestListener()
                    },
                    modifier = Modifier.testTag("dashboard_digest_toggle")
                )
            }
            if (optedIn && !listenerGranted) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRequestListener, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow notification access")
                }
            }
            if (optedIn && listenerGranted) {
                val calmPrefs = remember { CalmModePrefs(context) }
                var hideOthers by remember { mutableStateOf(calmPrefs.hideOthers) }
                Spacer(Modifier.height(8.dp))
                val setHideOthers: (Boolean) -> Unit = {
                    hideOthers = it
                    calmPrefs.hideOthers = it
                    if (!it) CalmSummary.clear(context)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().switchRow(hideOthers, setHideOthers),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Hide other notifications", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Other apps' notifications are taken out of the shade and collected here. Calls, texts, " +
                                "alarms and sign-in codes still come through. Read them from the \"Calm notifications\" " +
                                "summary, from the Quick Settings tile (swipe down twice, then edit tiles), or by " +
                                "swiping down on the dumb-phone home screen.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = hideOthers,
                        onCheckedChange = null,
                        modifier = Modifier.testTag("dashboard_hide_others")
                    )
                }
            }
            if (optedIn && listenerGranted) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_open_digest")
                ) { Text("Today's notifications") }
            }
        }
    }
}

/**
 * Nudges a parent who hasn't set the family passcode yet. The passcode is what protects this app,
 * lets a parent unlock or change limits from a kid's phone without their own in hand, and lifts a
 * "Lock now" from the lock screen - so most of the rest of the app works better once it exists.
 */
@Composable
private fun PasscodePromptCard(onSetPasscode: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.testTag("dashboard_passcode_prompt")
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Set your family passcode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "It protects this app, and it's how you unlock a locked phone or change limits on " +
                    "your kid's phone without your own in hand. It only takes a moment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSetPasscode, modifier = Modifier.fillMaxWidth()) { Text("Set passcode") }
        }
    }
}

/** Puts a pairing code on the clipboard (Android 13+ shows its own "Copied" confirmation). */
private fun copyPairingCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("OpenScreenTime pairing code", code))
}
