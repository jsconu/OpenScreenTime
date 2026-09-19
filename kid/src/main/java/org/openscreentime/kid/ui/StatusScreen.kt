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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openscreentime.kid.R
import org.openscreentime.kid.data.NotificationDigestStore
import org.openscreentime.kid.data.TextSize
import org.openscreentime.kid.data.ThemeMode
import org.openscreentime.kid.data.TipsStore
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.kid.data.label
import org.openscreentime.kid.util.PermissionActions
import org.openscreentime.kid.util.PermissionState
import org.openscreentime.kid.util.isNotificationListenerEnabled
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.TRACKING_DISPLAY_NOTE
import org.openscreentime.shared.model.currentDayIndex
import org.openscreentime.shared.model.currentDayKidTip
import org.openscreentime.shared.model.formatDuration

@Composable
fun StatusScreen(
    childName: String,
    permissions: PermissionState,
    themeMode: ThemeMode,
    textSize: TextSize,
    streakDays: Int,
    parentStatusLabel: String?,
    parentStats: DailyStats?,
    showUnlocks: Boolean,
    showNotifications: Boolean,
    permissionActions: PermissionActions,
    onCycleTheme: () -> Unit,
    onCycleTextSize: () -> Unit,
    onOpenColorSettings: () -> Unit,
    onOpenParentMode: () -> Unit,
    onOpenHelp: () -> Unit,
    onProposeChange: () -> Unit,
    onOpenNotificationDigest: () -> Unit,
    onRequestNotificationListener: () -> Unit,
    onUnpair: () -> Unit
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
        if (showUnlocks || showNotifications) {
            Spacer(Modifier.height(8.dp))
            TrackingCountsCard(showUnlocks = showUnlocks, showNotifications = showNotifications)
        }
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
        Spacer(Modifier.height(24.dp))

        PermissionRow(
            "Display over other apps",
            "Needed to show a screen when a limit is reached. If OpenScreenTime isn't " +
                "visible right away on the next screen, scroll down.",
            permissions.overlay,
            permissionActions.onRequestOverlay
        )
        PermissionRow(
            "Accessibility service",
            "Needed to detect which app is open. On the next screen, tap \"Downloaded " +
                "apps\" (or \"Installed apps\"), then find and turn on OpenScreenTime.",
            permissions.accessibility,
            permissionActions.onRequestAccessibility
        )
        PermissionRow(
            "Notifications",
            "Shows the ongoing monitoring notification",
            permissions.notifications,
            permissionActions.onRequestNotifications
        )
        PermissionRow(
            "Battery optimization",
            "Stops the system from killing tracking in the background",
            permissions.ignoringBatteryOptimizations,
            permissionActions.onRequestBatteryExemption
        )
        PermissionRow(
            "Website filter",
            "Blocks sites a parent has restricted, in any browser",
            permissions.vpn,
            permissionActions.onRequestVpn
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Some phone makers (Samsung, Xiaomi, and others) have their own extra battery " +
                "settings beyond this one - if tracking still stops unexpectedly after enabling " +
                "this, check this phone's battery/app settings for anything mentioning " +
                "\"auto-start,\" \"protected apps,\" or \"sleeping apps.\"",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start
        )

        Spacer(Modifier.height(24.dp))
        NotificationDigestCard(
            listenerGranted = isNotificationListenerEnabled(LocalContext.current),
            onOpen = onOpenNotificationDigest,
            onRequestListener = onRequestNotificationListener
        )

        Spacer(Modifier.height(24.dp))
        Text("Display", style = MaterialTheme.typography.labelLarge)
        ListItem(
            headlineContent = { Text("Theme") },
            supportingContent = { Text(themeMode.label()) },
            trailingContent = { TextButton(onClick = onCycleTheme) { Text("Change") } }
        )
        ListItem(
            headlineContent = { Text("Text size") },
            supportingContent = { Text(textSize.label()) },
            trailingContent = { TextButton(onClick = onCycleTextSize) { Text("Change") } }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Recommended: Android's built-in grayscale mode makes apps noticeably less " +
                "compelling to check, which research shows measurably cuts phone use. This app " +
                "can't turn it on directly - open Accessibility settings below, then look for " +
                "\"Color and motion\" or \"Color correction\" and turn on grayscale. The exact " +
                "wording and location varies by phone.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onOpenColorSettings) { Text("Open Accessibility settings") }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onProposeChange,
            modifier = Modifier.fillMaxWidth().testTag("status_propose_change")
        ) {
            Text("Suggest a change")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onOpenParentMode,
            modifier = Modifier.fillMaxWidth().testTag("status_parent_controls")
        ) {
            Text("Parent controls")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onUnpair, modifier = Modifier.fillMaxWidth()) {
            Text("Unpair this device")
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
    Card(modifier = Modifier.fillMaxWidth().testTag("status_tracking_counts")) {
        Column(Modifier.padding(12.dp)) {
            Text("Today so far", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(2.dp))
            if (showUnlocks) Text("${usageStore.unlockCount} unlocks", style = MaterialTheme.typography.bodyMedium)
            if (showNotifications) {
                Text("${usageStore.notificationCount} notifications", style = MaterialTheme.typography.bodyMedium)
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
 * Explains the ongoing notification's status icon (see #9's calm status indicator) - the
 * icon itself only ever appears in the system notification, so this is the one place in
 * the app that says what it actually means, for whenever it's glanced at without opening
 * the app. See #30 - also says *why* this is the only signal shown here (no exact numbers,
 * no report), since an unexplained limitation reads as arbitrary or secretive otherwise.
 */
@Composable
private fun StatusIconLegend() {
    Column(modifier = Modifier.testTag("status_icon_legend")) {
        Text("What the status icon means", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        StatusIconLegendRow(R.drawable.ic_status_good, "Comfortably under today's goal")
        StatusIconLegendRow(R.drawable.ic_status_caution, "Approaching today's goal")
        StatusIconLegendRow(R.drawable.ic_status_stop, "At or over today's goal")
        Spacer(Modifier.height(6.dp))
        Text(
            "This app deliberately doesn't show exact numbers or a detailed report here - " +
                "just this simple signal, so screen time stays something to be aware of, not " +
                "something to obsess over checking. Ask a parent if you want to talk through " +
                "the details.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("status_icon_legend_why")
        )
    }
}

@Composable
private fun StatusIconLegendRow(iconRes: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
        headlineContent = { Text("Calm notification list") },
        supportingContent = {
            Text("A plain, read-only digest of today's notifications on this phone, grouped by app. Nothing is sent to a parent.")
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
    if (!listenerGranted) {
        PermissionRow(
            "Notification access",
            "Needed for the digest above, and separately to mute texts from numbers " +
                "that aren't allowed through during bedtime (see Parent controls). " +
                "Off until you turn it on in system settings.",
            granted = false,
            onClick = onRequestListener
        )
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
private fun PermissionRow(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
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
