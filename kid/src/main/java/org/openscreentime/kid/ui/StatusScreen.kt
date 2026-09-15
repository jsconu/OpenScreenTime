package org.openscreentime.kid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openscreentime.kid.util.PermissionState

@Composable
fun StatusScreen(
    childName: String,
    permissions: PermissionState,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenParentMode: () -> Unit,
    onUnpair: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            "Hi, $childName",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("status_greeting")
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (permissions.allGranted) "Screen time monitoring is active." else "A few permissions are needed to finish setup.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        PermissionRow(
            "Display over other apps",
            "Needed to show a screen when a limit is reached",
            permissions.overlay,
            onRequestOverlay
        )
        PermissionRow(
            "Accessibility service",
            "Needed to detect which app is open",
            permissions.accessibility,
            onRequestAccessibility
        )
        PermissionRow(
            "Notifications",
            "Shows the ongoing monitoring notification",
            permissions.notifications,
            onRequestNotifications
        )

        Spacer(Modifier.weight(1f, fill = true))
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

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            if (granted) {
                Text("Granted", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelMedium)
            } else {
                TextButton(onClick = onClick) { Text("Fix") }
            }
        }
    )
}
