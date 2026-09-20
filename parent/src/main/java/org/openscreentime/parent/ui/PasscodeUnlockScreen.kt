package org.openscreentime.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.parent.data.AppLockPrefs
import org.openscreentime.parent.util.DeviceAuth
import org.openscreentime.shared.model.PasscodeInfo
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.shared.util.PasscodeHasher

private const val MAX_ATTEMPTS = 5

/**
 * The lock screen in front of the parent app. Opens with the family passcode; if a parent turned it on
 * (Passcode settings), also with this phone's fingerprint or screen lock; and a forgotten passcode can be
 * replaced by proving the account password ([ForgotPasscodeDialog]). Recovery deliberately asks for the
 * account password and NOT the phone's screen lock: anyone who knows the phone's PIN could otherwise reset
 * the passcode and walk into the parent controls.
 */
@Composable
fun PasscodeUnlockScreen(
    passcode: PasscodeInfo,
    repository: FamilyRepository,
    onUnlocked: () -> Unit,
    onPasscodeReset: (PasscodeInfo) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    var verifying by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    val locked = attempts >= MAX_ATTEMPTS

    val deviceAuthOn = remember { AppLockPrefs(context).useDeviceAuth && DeviceAuth.isAvailable(context) }
    fun promptDeviceAuth() {
        if (activity == null) return
        DeviceAuth.authenticate(activity, "Unlock OpenScreenTime", "Use your fingerprint or screen lock") { ok ->
            if (ok) onUnlocked()
        }
    }
    // Offer it straight away, the way a banking app does; the passcode below is always still there.
    LaunchedEffect(Unit) { if (deviceAuthOn) promptDeviceAuth() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter passcode", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = entered,
            onValueChange = { entered = it.filter(Char::isDigit) },
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
            enabled = entered.isNotBlank() && !locked && !verifying,
            onClick = {
                verifying = true
                scope.launch {
                    // PBKDF2 is deliberately slow - keep it off the UI thread.
                    val ok = withContext(Dispatchers.Default) {
                        PasscodeHasher.verify(entered, passcode.salt, passcode.hash)
                    }
                    verifying = false
                    if (ok) {
                        onUnlocked()
                    } else {
                        attempts++
                        entered = ""
                        error = if (attempts >= MAX_ATTEMPTS) "Too many incorrect attempts. Try again later, or use \"Forgot passcode?\"." else "Incorrect passcode."
                    }
                }
            }
        ) {
            Text(if (verifying) "Checking..." else "Unlock")
        }
        if (deviceAuthOn) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { promptDeviceAuth() }, modifier = Modifier.testTag("unlock_device_auth")) {
                Text("Use fingerprint or screen lock")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showForgot = true }, modifier = Modifier.testTag("forgot_passcode")) {
            Text("Forgot passcode?")
        }
    }

    if (showForgot) {
        ForgotPasscodeDialog(
            repository = repository,
            onDismiss = { showForgot = false },
            onReset = { info ->
                showForgot = false
                onPasscodeReset(info)
            }
        )
    }
}

/**
 * Replaces a forgotten family passcode: first proves the person knows the account password (so a child
 * holding the phone can't do it), then sets a new passcode. The new one is saved for this account and
 * for every paired kid device, exactly like changing it from Passcode settings.
 */
@Composable
private fun ForgotPasscodeDialog(
    repository: FamilyRepository,
    onDismiss: () -> Unit,
    onReset: (PasscodeInfo) -> Unit
) {
    val scope = rememberCoroutineScope()
    var verified by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var newPasscode by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (verified) "Choose a new passcode" else "Reset your passcode") },
        text = {
            Column {
                if (!verified) {
                    Text(
                        "To make sure it's you, enter the password for your OpenScreenTime account " +
                            "(the one you sign in with). This is not your phone's screen lock.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Account password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("forgot_account_password")
                    )
                } else {
                    Text(
                        "This replaces the old passcode here and on every paired kid phone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPasscode,
                        onValueChange = { newPasscode = it.filter(Char::isDigit) },
                        label = { Text("New passcode (4-6 digits)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it.filter(Char::isDigit) },
                        label = { Text("Confirm passcode") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && if (verified) newPasscode.length in 4..6 else password.isNotEmpty(),
                modifier = Modifier.testTag("forgot_continue"),
                onClick = {
                    error = null
                    busy = true
                    scope.launch {
                        try {
                            if (!verified) {
                                if (repository.verifyAccountPassword(password)) {
                                    verified = true
                                    password = ""
                                } else {
                                    error = "That isn't the account password."
                                }
                            } else if (newPasscode != confirm) {
                                error = "Passcodes don't match."
                            } else {
                                val parentUid = repository.currentUid ?: throw IllegalStateException("Not signed in.")
                                val (hash, salt) = withContext(Dispatchers.Default) {
                                    val salt = PasscodeHasher.randomSalt()
                                    PasscodeHasher.hash(newPasscode, salt) to salt
                                }
                                repository.setParentPasscode(parentUid, hash, salt)
                                onReset(PasscodeInfo(hash, salt))
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Something went wrong. Check your connection and try again."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Working..." else if (verified) "Save passcode" else "Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}
