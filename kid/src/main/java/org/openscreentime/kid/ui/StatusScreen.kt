package org.openscreentime.kid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.openscreentime.kid.Backend
import org.openscreentime.sharedui.mergedRow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.openscreentime.kid.R
import org.openscreentime.kid.data.NotificationDigestStore
import org.openscreentime.kid.data.TextSize
import org.openscreentime.kid.data.ThemeMode
import org.openscreentime.kid.data.TipsStore
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.kid.monitor.currentStatus
import org.openscreentime.kid.data.label
import org.openscreentime.kid.util.PermissionActions
import org.openscreentime.kid.util.PermissionState
import org.openscreentime.kid.util.isNotificationListenerEnabled
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.StatusTier
import org.openscreentime.shared.model.statusNotificationMessage
import org.openscreentime.sharedui.StatusIconGuide
import org.openscreentime.shared.model.TRACKING_DISPLAY_NOTE
import org.openscreentime.shared.model.currentDayIndex
import org.openscreentime.shared.model.currentDayKidTip
import org.openscreentime.shared.model.formatDuration

@Composable
fun StatusScreen(
    childName: String,
    permissions: PermissionState,
    streakDays: Int,
    parentStatusLabel: String?,
    parentStats: DailyStats?,
    showUnlocks: Boolean,
    showNotifications: Boolean,
    onOpenSettings: () -> Unit,
    onOpenParentMode: () -> Unit,
    onOpenHelp: () -> Unit,
    onProposeChange: () -> Unit,
    onOpenNotificationDigest: () -> Unit,
    onRequestNotificationListener: () -> Unit,
    /** Local builds only: whether this phone has been linked to a parent's phone yet. */
    showNearbyLink: Boolean = false,
    nearbyLinkedTo: String? = null,
    nearbyLastSyncedAtMs: Long = 0,
    nearbySyncing: Boolean = false,
    onLinkParentPhone: () -> Unit = {},
    onSyncNow: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Hi, $childName",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).testTag("status_greeting")
            )
            TextButton(onClick = onOpenHelp, modifier = Modifier.testTag("status_help")) { Text("?") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (permissions.allGranted) "Screen time monitoring is active." else "A few permissions are needed to finish setup.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (!permissions.allGranted) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onOpenSettings, modifier = Modifier.testTag("status_finish_setup")) {
                Text("Finish setup in Settings")
            }
        }
        Spacer(Modifier.height(12.dp))
        MyScreenTimeCard()
        if (parentStatusLabel != null && parentStats != null) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth().testTag("status_parent_status")) {
                Column(Modifier.padding(12.dp)) {
                    Text("Parent's screen time today", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(parentStatusLabel, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatDuration(parentStats.totalScreenTimeMs)} - " +
                            "${parentStats.unlockCount} unlock${if (parentStats.unlockCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("status_parent_numbers")
                    )
                }
            }
        }
        if (showUnlocks || showNotifications) {
            Spacer(Modifier.height(8.dp))
            TrackingCountsCard(showUnlocks = showUnlocks, showNotifications = showNotifications)
        }
        Spacer(Modifier.height(12.dp))
        NotificationDigestCard(
            listenerGranted = isNotificationListenerEnabled(LocalContext.current),
            onOpen = onOpenNotificationDigest,
            onRequestListener = onRequestNotificationListener
        )
        Spacer(Modifier.height(12.dp))
        StatusIconLegend()
        Spacer(Modifier.height(12.dp))
        TipOfTheDayCard()
        if (streakDays > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "$streakDays day${if (streakDays == 1) "" else "s"} in a row under your goal",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("status_streak")
            )
        }

        Spacer(Modifier.height(24.dp))
        // Linking is the first thing a kid's phone needs in a local build, so it belongs here in
        // plain sight rather than buried in Settings - which is where it was, and nobody found it.
        if (showNearbyLink && nearbyLinkedTo == null) {
            Button(
                onClick = onLinkParentPhone,
                modifier = Modifier.fillMaxWidth().testTag("status_link_parent")
            ) {
                Text("Scan a parent's code")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Ask a parent to open OpenScreenTime on their phone and tap \"Link a kid's phone\". " +
                    "Until then, limits are set on this phone in Parent controls.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        if (showNearbyLink && nearbyLinkedTo != null) {
            Text(
                if (nearbySyncing) "Sending to $nearbyLinkedTo..." else lastSyncedLabel(nearbyLastSyncedAtMs, nearbyLinkedTo),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("status_nearby_state")
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = !nearbySyncing,
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth().testTag("status_sync_now")
            ) { Text("Send to their phone now") }
            Spacer(Modifier.height(4.dp))
            // Said on the button itself, every time: without it, a tap that does nothing looks
            // like a broken app rather than two phones being in different places.
            Text(
                "Only works while both phones are on the same Wi-Fi and awake.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!Backend.IS_LOCAL || nearbyLinkedTo != null) {
            OutlinedButton(
                onClick = onProposeChange,
                modifier = Modifier.fillMaxWidth().testTag("status_propose_change")
            ) {
                Text("Suggest a change")
            }
        } else if (Backend.IS_LOCAL) {
            Text(
                "Want different limits? Ask a parent - they can change them on this phone in " +
                    "Parent controls.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("status_local_ask_in_person")
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onOpenParentMode,
            modifier = Modifier.fillMaxWidth().testTag("status_parent_controls")
        ) {
            Text("Parent controls")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().testTag("status_settings")
        ) {
            Text("Settings")
        }
    }
}

/**
 * See #35 - today's unlock and/or notification count, shown only when a parent has both turned
 * that category's tracking on and chosen to display it here. Always carries the note that
 * watching counts can feed compulsive checking, since a running number is exactly that risk.
 */
@Composable
private fun TrackingCountsCard(showUnlocks: Boolean, showNotifications: Boolean) {
    val context = LocalContext.current
    val usageStore = remember { UsageStore(context) }
    // The stores aren't Compose state, so re-read them on a slow tick rather than showing whatever
    // they held when this card first composed. (Slow on purpose: it's not a live counter.)
    val counts by produceState(usageStore.unlockCount to usageStore.notificationCount) {
        while (true) {
            delay(15_000)
            value = usageStore.unlockCount to usageStore.notificationCount
        }
    }
    Card(modifier = Modifier.fillMaxWidth().testTag("status_tracking_counts")) {
        Column(Modifier.padding(12.dp)) {
            Text("Today so far", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(2.dp))
            if (showUnlocks) Text("${counts.first} unlocks", style = MaterialTheme.typography.bodyMedium)
            if (showNotifications) {
                Text("${counts.second} notifications", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                TRACKING_DISPLAY_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * "My screen time" - the same thumbs up / open hand / stop that sits in the top-left of the status
 * bar, with the same short message the notification shade shows. It comes from [currentStatus], the
 * one function the status-bar icon also uses, so the two always match. Deliberately no numbers: a
 * child sees the calm signal only (see the guide below); the details are for a parent.
 */
@Composable
private fun MyScreenTimeCard() {
    val context = LocalContext.current
    val usageStore = remember { UsageStore(context) }
    val status by produceState(currentStatus(usageStore)) {
        while (true) {
            delay(10_000)
            value = currentStatus(usageStore)
        }
    }
    val iconRes = when (status.tier) {
        StatusTier.STOP -> R.drawable.ic_status_stop
        StatusTier.CAUTION -> R.drawable.ic_status_caution
        StatusTier.GOOD -> R.drawable.ic_status_good
    }
    Card(modifier = Modifier.fillMaxWidth().testTag("status_my_screen_time")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("My screen time", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    statusNotificationMessage(status.tier, status.pausedByLockOrBedtime),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("status_my_screen_time_message")
                )
            }
        }
    }
}

/**
 * Explains the calm status icons (see #9) - where they appear (top-left of the status bar), what
 * thumbs up / open hand / stop mean, and what pulling the shade down shows. The icons only ever
 * appear in the system status bar, so this is the one place in the app that says what they mean.
 * See #30 - also says *why* this is the only signal shown here (no exact numbers, no report), since
 * an unexplained limitation reads as arbitrary or secretive otherwise. The parent app shows the
 * same guide, from the same shared composable.
 */
@Composable
private fun StatusIconLegend() {
    StatusIconGuide(
        goodIcon = R.drawable.ic_status_good,
        cautionIcon = R.drawable.ic_status_caution,
        stopIcon = R.drawable.ic_status_stop,
        footnote = "This app deliberately doesn't show exact numbers or a detailed report here - " +
            "just this simple signal, so screen time stays something to be aware of, not " +
            "something to obsess over checking. Ask a parent if you want to talk through " +
            "the details."
    )
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
private fun TipOfTheDayCard() {
    val context = LocalContext.current
    val tipsStore = remember { TipsStore(context) }
    val dayIndex = remember { currentDayIndex() }
    var acknowledged by remember { mutableStateOf(tipsStore.acknowledgedDayIndex == dayIndex) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("status_tip_card")
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Off-screen idea",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                currentDayKidTip(),
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
                    },
                    modifier = Modifier.testTag("status_tip_checkbox")
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
private fun NotificationDigestCard(
    listenerGranted: Boolean,
    onOpen: () -> Unit,
    onRequestListener: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { NotificationDigestStore(context) }
    var optedIn by remember { mutableStateOf(store.optedIn) }

    Text("Optional", style = MaterialTheme.typography.labelLarge)
    ListItem(
        modifier = Modifier.mergedRow(),
        headlineContent = { Text("Calm notification list") },
        supportingContent = {
            Text("A plain, read-only digest of today's notifications on this phone, grouped by app.")
        },
        trailingContent = {
            Switch(
                checked = optedIn,
                onCheckedChange = { checked ->
                    optedIn = checked
                    store.optedIn = checked
                    if (checked && !listenerGranted) onRequestListener()
                },
                modifier = Modifier.testTag("status_digest_toggle")
            )
        }
    )
    if (optedIn && !listenerGranted) {
        OutlinedButton(onClick = onRequestListener, modifier = Modifier.fillMaxWidth()) {
            Text("Allow notification access")
        }
    }
    if (optedIn && listenerGranted) {
        OutlinedButton(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().testTag("status_open_digest")
        ) {
            Text("Today's notifications")
        }
    }
}

@Composable
internal fun PermissionRow(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    // Ungranted rows are the thing that needs attention - a tinted background makes them
    // stand out in the checklist instead of blending in with everything already fixed.
    Surface(
        color = if (granted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
            trailingContent = {
                if (granted) {
                    Text("Granted", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelMedium)
                } else {
                    Button(onClick = onClick) { Text("Fix") }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

/**
 * What a kid sees about the link, in their own terms. Never "connected" - the two phones are only
 * ever in touch for a moment at a time, and a standing green light would be a lie.
 */
private fun lastSyncedLabel(lastSyncedAtMs: Long, peerName: String, nowMs: Long = System.currentTimeMillis()): String {
    if (lastSyncedAtMs <= 0) return "Linked to $peerName. Nothing sent yet - this works when you're both on the same Wi-Fi."
    val minutes = ((nowMs - lastSyncedAtMs) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 2 -> "Sent to $peerName just now"
        minutes < 60 -> "Last sent to $peerName $minutes minutes ago"
        minutes < 60 * 24 -> "Last sent to $peerName ${minutes / 60} hour${if (minutes / 60 == 1L) "" else "s"} ago"
        else -> "Last sent to $peerName ${minutes / (60 * 24)} day${if (minutes / (60 * 24) == 1L) "" else "s"} ago"
    }
}
