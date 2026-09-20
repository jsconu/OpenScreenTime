package org.openscreentime.parent.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openscreentime.parent.util.PermissionState
import org.openscreentime.parent.util.isNotificationListenerEnabled

/**
 * Opting in to self-tracking (see #8): the parent's own device, tracked and limited the same way a
 * paired kid's device is. Shown only until they start; from then on tapping the "Me" tile goes
 * straight to [ChildDetailScreen] (their own screen time), and the permission checklist lives on
 * its own screen, [SelfPermissionsScreen].
 */
@Composable
fun SelfTrackingScreen(
    onStartTracking: suspend () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var starting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My screen time") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                "Track and limit your own screen time on this device, the same way you " +
                    "can for your kids. Parents modeling healthy limits themselves is one of " +
                    "the more effective things a family can actually do about screen time.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Button(
                enabled = !starting,
                onClick = {
                    starting = true
                    scope.launch {
                        onStartTracking()
                        starting = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (starting) "Starting..." else "Start tracking my screen time")
            }
        }
    }
}

/** What self-tracking needs turned on for this phone, and where to stop it. Its own screen. */
@Composable
fun SelfPermissionsScreen(
    permissions: PermissionState,
    onStopTracking: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestNotificationListener: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                if (permissions.allGranted) "Self-tracking is active." else "A few permissions are needed on this device too.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            PermissionRow(
                "Display over other apps",
                "Needed to show a screen when a limit is reached. If OpenScreenTime " +
                    "isn't visible right away on the next screen, scroll down.",
                permissions.overlay,
                onRequestOverlay
            )
            PermissionRow(
                "Accessibility service",
                "Needed to detect which app is open. On the next screen, tap " +
                    "\"Downloaded apps\" (or \"Installed apps\"), then find and turn on " +
                    "OpenScreenTime.",
                permissions.accessibility,
                onRequestAccessibility
            )
            PermissionRow(
                "Notifications",
                "Shows the ongoing monitoring notification",
                permissions.notifications,
                onRequestNotifications
            )
            PermissionRow(
                "Battery optimization",
                "Stops the system from killing tracking in the background",
                permissions.ignoringBatteryOptimizations,
                onRequestBatteryExemption
            )
            PermissionRow(
                "Notification access (optional)",
                "Only needed for notification-count tracking or the calm notification list. " +
                    "Counts only - nothing about a notification's content is kept.",
                isNotificationListenerEnabled(LocalContext.current),
                onRequestNotificationListener
            )

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onStopTracking, modifier = Modifier.fillMaxWidth()) {
                Text("Stop tracking my screen time")
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
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
