package org.openscreentime.sharedui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.TRACKING_DISPLAY_NOTE
import org.openscreentime.shared.model.TrackingToggle

/**
 * See #35 - optional, parent-controlled tracking categories, off by default. The same four
 * toggles serve a kid's profile and the parent's own self profile (which is just a profile with
 * isSelf = true); the "show on their phone" options only make sense for a kid, so they're hidden
 * for self. The plain daily unlock count is always kept (it drives the unlock goal); everything
 * beyond that - first-app-after-unlock, notification counts, and their reporting - waits for a toggle.
 */
fun LazyListScope.trackingSection(
    child: ChildProfile,
    onToggle: (TrackingToggle, Boolean) -> Unit
) {
    item {
        Column(Modifier.padding(16.dp)) {
            Text("Optional tracking", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Off by default. The plain daily unlock count is always kept for the unlock goal; " +
                    "turning a category on adds more detail, and shows it in the weekly report and " +
                    "its daily digest.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    item {
        TrackingToggleRow(
            title = "Track unlocks",
            description = "Adds which app is opened first after each unlock, and puts unlocks in the weekly report.",
            checked = child.trackUnlocks,
            onCheckedChange = { onToggle(TrackingToggle.TRACK_UNLOCKS, it) }
        )
    }
    if (!child.isSelf && child.trackUnlocks) {
        item {
            TrackingToggleRow(
                title = "Show unlocks on ${child.name}'s phone",
                description = "Off by default.",
                checked = child.showUnlocksOnKid,
                onCheckedChange = { onToggle(TrackingToggle.SHOW_UNLOCKS_ON_KID, it) }
            )
        }
    }
    item {
        TrackingToggleRow(
            title = "Track notification counts",
            description = "Counts notifications received, overall and by app - counts only, never " +
                "content. Needs notification access granted on the device.",
            checked = child.trackNotifications,
            onCheckedChange = { onToggle(TrackingToggle.TRACK_NOTIFICATIONS, it) }
        )
    }
    if (!child.isSelf && child.trackNotifications) {
        item {
            TrackingToggleRow(
                title = "Show notification counts on ${child.name}'s phone",
                description = "Off by default.",
                checked = child.showNotificationsOnKid,
                onCheckedChange = { onToggle(TrackingToggle.SHOW_NOTIFICATIONS_ON_KID, it) }
            )
        }
    }
    item {
        TrackingToggleRow(
            title = "Track websites",
            description = "Counts which sites are looked up while a browser is open - site names only, never " +
                "pages, searches or time. Needs the website filter turned on on that phone, and only sees " +
                "browsers that use the phone's own DNS.",
            checked = child.trackWebsites,
            onCheckedChange = { onToggle(TrackingToggle.TRACK_WEBSITES, it) }
        )
    }
    if (!child.isSelf && (child.showUnlocksOnKid || child.showNotificationsOnKid)) {
        item {
            Text(
                "They'll see this note next to the numbers: \"$TRACKING_DISPLAY_NOTE\"",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun TrackingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.switchRow(checked, onCheckedChange),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) }
    )
}
