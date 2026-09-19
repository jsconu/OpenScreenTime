package org.openscreentime.kid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openscreentime.kid.data.TextSize
import org.openscreentime.kid.data.ThemeMode
import org.openscreentime.kid.data.label
import org.openscreentime.kid.util.PermissionActions
import org.openscreentime.kid.util.PermissionState
import org.openscreentime.kid.util.isNotificationListenerEnabled

/**
 * Everything that's set up once rather than looked at daily - the permission checklist, display
 * options, and unpairing - so the home screen can stay about today. See the home screen's "Finish
 * setup in Settings" button, which appears whenever a permission is still missing.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun KidSettingsScreen(
    permissions: PermissionState,
    themeMode: ThemeMode,
    textSize: TextSize,
    permissionActions: PermissionActions,
    onCycleTheme: () -> Unit,
    onCycleTextSize: () -> Unit,
    onOpenColorSettings: () -> Unit,
    onRequestNotificationListener: () -> Unit,
    /** Only offered here while no family passcode is set; otherwise it lives in Parent controls. */
    showUnpair: Boolean,
    onUnpair: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text("Permissions", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
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

        PermissionRow(
            "Notification access",
            "Optional. Needed for the calm notification list, and to mute texts from numbers " +
                "that aren't allowed through during bedtime (see Parent controls). Off until " +
                "you turn it on in system settings.",
            isNotificationListenerEnabled(LocalContext.current),
            onRequestNotificationListener
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

        if (showUnpair) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onUnpair, modifier = Modifier.fillMaxWidth()) {
                Text("Unpair this device")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    }
}
