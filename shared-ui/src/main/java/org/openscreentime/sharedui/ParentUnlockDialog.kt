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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

/**
 * The "Parent unlock" prompt on a locked screen: a parent enters the family passcode to lift the
 * lock without needing the other phone. The caller checks the passcode and reports [error]; this
 * only collects it. Used by both the kid and parent apps' lock screens.
 */
@Composable
fun ParentUnlockDialog(
    verifying: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!verifying) onDismiss() },
        title = { Text("Parent unlock") },
        text = {
            Column {
                Text(
                    "Enter the family passcode to unlock. You can lock again any time from the parent app.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it.filter(Char::isDigit) },
                    label = { Text("Passcode") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.testTag("parent_unlock_passcode")
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(passcode) },
                enabled = passcode.isNotBlank() && !verifying,
                modifier = Modifier.testTag("parent_unlock_submit")
            ) { Text(if (verifying) "Checking..." else "Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !verifying) { Text("Cancel") } }
    )
}
