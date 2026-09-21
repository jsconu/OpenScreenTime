package org.openscreentime.sharedui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Shown on the launch after a crash, so a tester can copy what went wrong and send it. The details are only the error's
 * stack trace (which code failed), the app version and the time - nothing about the person, the family or the screen.
 */
@Composable
fun CrashReportDialog(details: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OpenScreenTime closed unexpectedly") },
        text = {
            Column {
                Text(
                    "Sorry about that. If you're helping test, tap Copy details and send them along. They contain only " +
                        "which part of the app failed, not anything about you or your family.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()).testTag("crash_details")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("OpenScreenTime crash details", details))
                onDismiss()
            }, modifier = Modifier.testTag("crash_copy")) { Text("Copy details") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } }
    )
}
