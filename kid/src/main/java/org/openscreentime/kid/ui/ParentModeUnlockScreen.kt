package org.openscreentime.kid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.util.PasscodeHasher

private const val MAX_ATTEMPTS = 5

/**
 * Gate in front of [ParentControlsScreen]: verifies the family passcode entered on
 * this device against the hash/salt synced from the parent's account. Verification
 * is entirely local - there's no network round trip, so it works offline too.
 */
@Composable
fun ParentModeUnlockScreen(
    child: ChildProfile?,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var passcode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    var verifying by remember { mutableStateOf(false) }

    val hash = child?.parentPasscodeHash
    val salt = child?.parentPasscodeSalt
    val locked = attempts >= MAX_ATTEMPTS

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Parent controls", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (hash == null || salt == null) {
            Text(
                "Ask a parent to set a family passcode in the OpenScreenTime app first.",
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "Enter the family passcode to change limits on this device.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = passcode,
                onValueChange = { passcode = it.filter(Char::isDigit) },
                label = { Text("Passcode") },
                singleLine = true,
                enabled = !locked,
                visualTransformation = PasswordVisualTransformation()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = passcode.isNotBlank() && !locked && !verifying,
                onClick = {
                    verifying = true
                    scope.launch {
                        // PBKDF2 is deliberately slow - keep it off the UI thread.
                        val ok = withContext(Dispatchers.Default) { PasscodeHasher.verify(passcode, salt, hash) }
                        verifying = false
                        if (ok) {
                            onUnlocked()
                        } else {
                            attempts++
                            passcode = ""
                            error = if (attempts >= MAX_ATTEMPTS) {
                                "Too many incorrect attempts. Come back later."
                            } else {
                                "Incorrect passcode."
                            }
                        }
                    }
                }
            ) {
                Text(if (verifying) "Checking..." else "Unlock")
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Back") }
    }
}
