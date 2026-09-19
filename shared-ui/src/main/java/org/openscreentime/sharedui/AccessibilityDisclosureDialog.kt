package org.openscreentime.sharedui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The prominent in-app disclosure shown *before* sending someone to Android's Accessibility
 * settings. It says plainly what the accessibility service does and doesn't do, and asks for an
 * explicit "I agree" - Google Play requires this for any app that uses the Accessibility API, and
 * it's the right thing to do regardless: an accessibility service is a powerful permission and the
 * person turning it on should know exactly why.
 *
 * Shared by the kid and parent apps; [onKidDevice] only changes who "this device" belongs to.
 */
@Composable
fun AccessibilityDisclosureDialog(
    onKidDevice: Boolean,
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you turn on the Accessibility service") },
        text = {
            Column {
                Text(
                    if (onKidDevice) {
                        "OpenScreenTime uses Android's Accessibility service so it can tell which app is " +
                            "in front on this phone. It uses that to add up time per app, show a reminder " +
                            "when a limit is reached, and show the block screen when time is up."
                    } else {
                        "OpenScreenTime uses Android's Accessibility service so it can tell which app is " +
                            "in front on this phone. It uses that to add up your own time per app, " +
                            "show a reminder when a limit you set is reached, and show the block screen."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "It does not read what's on the screen, what you type, passwords, messages, or " +
                        "anything inside an app. It only sees the name of the app that's in front. " +
                        "Nothing is sold or used for advertising.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "On the next screen, find OpenScreenTime in the list and turn it on. You can turn " +
                        "it off there at any time.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = onAgree) { Text("I agree - continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}
