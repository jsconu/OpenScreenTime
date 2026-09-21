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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.util.PasscodeAttemptStore
import org.openscreentime.shared.util.PasscodeHasher
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.testTag

/**
 * Gate in front of [ParentControlsScreen]: verifies the family passcode entered on
 * this device against the hash/salt synced from the parent's account. Verification
 * is entirely local - there's no network round trip, so it works offline too.
 */
@Composable
fun ParentModeUnlockScreen(
    child: ChildProfile?,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
    /**
     * Set the family passcode on this phone. Offered only in a local build with no passcode yet:
     * there, no parent's account is going to supply one, and without it Parent controls could never
     * be opened at all. Null where a parent's app owns the passcode.
     */
    onSetPasscodeHere: (suspend (String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val attemptStore = remember { PasscodeAttemptStore(context) }
    var passcode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(attemptStore.isLocked()) }
    var verifying by remember { mutableStateOf(false) }

    val hash = child?.parentPasscodeHash
    val salt = child?.parentPasscodeSalt

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Parent controls", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if ((hash == null || salt == null) && onSetPasscodeHere != null) {
            // Nobody else can give this phone a passcode, so it is set here, once, by whoever is
            // holding it - which at setup time is a parent.
            var chosen by remember { mutableStateOf("") }
            var confirm by remember { mutableStateOf("") }
            var saving by remember { mutableStateOf(false) }
            Text(
                "Choose a family passcode for this phone. You'll need it to change limits here, " +
                    "and it's the only thing between a curious kid and their own settings.",
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = chosen,
                onValueChange = { chosen = it.filter(Char::isDigit).take(6) },
                label = { Text("Passcode (4-6 digits)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("kid_set_passcode")
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it.filter(Char::isDigit).take(6) },
                label = { Text("Enter it again") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !saving && chosen.length >= 4,
                onClick = {
                    when {
                        chosen != confirm -> error = "Those don't match."
                        else -> {
                            saving = true
                            error = null
                            scope.launch {
                                runCatching { onSetPasscodeHere(chosen) }
                                    .onSuccess { onUnlocked() }
                                    .onFailure {
                                        error = "Couldn't save that. Try again."
                                        saving = false
                                    }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("kid_save_passcode")
            ) { Text(if (saving) "Saving..." else "Set passcode") }
        } else if (hash == null || salt == null) {
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
                            attemptStore.recordSuccess()
                            onUnlocked()
                        } else {
                            locked = attemptStore.recordFailure()
                            passcode = ""
                            error = if (locked) {
                                "Too many incorrect attempts. Try again in ${attemptStore.minutesRemaining()} minutes."
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
