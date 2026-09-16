package org.openscreentime.sharedui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openscreentime.shared.model.formatHHmm
import org.openscreentime.shared.model.parseHHmm

/**
 * The three limit-editing dialogs, called from three places that all write the same
 * ChildProfile fields: the parent app's ChildDetailScreen, a kid device's passcode-gated
 * ParentControlsScreen, and the kid's passcode-free ProposeChangeScreen (#14, hence
 * [MinutesInputDialog]'s `confirmLabel` parameter - "Send suggestion" there instead of
 * "Save", since a proposal isn't applied directly). See the architecture review's
 * Candidate 1: the first two were identical, byte-for-byte, except one already-drifted
 * word in [UnlockGoalInputDialog]'s helper text - real evidence the duplication itself
 * was the bug; the third turned up only once this module existed to consolidate into.
 *
 * Deliberately independent of `:shared`'s Firestore/ChildProfile types: each dialog takes
 * only primitives and callbacks, so a caller wires it to whichever repository call it
 * needs without this module knowing anything about Firestore.
 */
@Composable
fun MinutesInputDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    confirmLabel: String = "Save"
) {
    var text by remember { mutableStateOf(initialMinutes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                label = { Text("Minutes per day") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toIntOrNull()?.let(onConfirm) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun UnlockGoalInputDialog(
    initialGoal: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    var text by remember { mutableStateOf(initialGoal?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily unlock goal") },
        text = {
            Column {
                Text(
                    "Informational only - never enforced or blocked, just shown alongside actual unlocks.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit) },
                    label = { Text("Unlocks per day (blank = no goal)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toIntOrNull()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BedtimeWindowDialog(
    initialStartMinutes: Int?,
    initialEndMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?, Int?) -> Unit
) {
    var startText by remember { mutableStateOf(initialStartMinutes?.let(::formatHHmm) ?: "") }
    var endText by remember { mutableStateOf(initialEndMinutes?.let(::formatHHmm) ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bedtime") },
        text = {
            Column {
                Text(
                    "Blocks every app during this window, independent of the daily limit. " +
                        "Leave both blank to turn it off.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("Start (24h, e.g. 21:00)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("End (24h, e.g. 07:00)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = parseHHmm(startText)
                val end = parseHHmm(endText)
                if (start != null && end != null) onConfirm(start, end) else onConfirm(null, null)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
