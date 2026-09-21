package org.openscreentime.parent.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import org.openscreentime.sharedui.ScreenUnavailable
import org.openscreentime.sharedui.mergedRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.parent.data.AppLockPrefs
import org.openscreentime.parent.util.DeviceAuth
import org.openscreentime.shared.util.PasscodeHasher
import org.openscreentime.shared.repo.FamilyRepository

/**
 * Sets or changes the one family passcode: it locks this app, and can also be entered
 * on a paired kid device to unlock "parent mode" there (see ParentModeUnlockScreen in
 * the kid app).
 */
@Composable
fun PasscodeSettingsScreen(repository: FamilyRepository, onBack: () -> Unit) {
    val parentUid = repository.currentUid ?: run {
        ScreenUnavailable(message = "You've been signed out. Go back and sign in again.", onBack = onBack)
        return
    }
    val scope = rememberCoroutineScope()

    var hasPasscode by remember { mutableStateOf<Boolean?>(null) }
    var current by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lockPrefs = remember { AppLockPrefs(context) }
    val deviceAuthAvailable = remember { DeviceAuth.isAvailable(context) }
    var useDeviceAuth by remember { mutableStateOf(lockPrefs.useDeviceAuth) }

    LaunchedEffect(Unit) {
        hasPasscode = repository.getParentPasscode(parentUid) != null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family passcode") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text(
                when (hasPasscode) {
                    true -> "This locks the app, and can be entered on a paired kid device to unlock \"Parent controls\" there. Set a new one to replace it."
                    false -> "Set a passcode to lock this app and to allow \"Parent controls\" on a paired kid device."
                    null -> ""
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (deviceAuthAvailable) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.mergedRow()) {
                    Column(Modifier.weight(1f)) {
                        Text("Unlock with fingerprint or screen lock", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Open this app with this phone's fingerprint, face, or screen lock instead of typing " +
                                "the passcode. Anyone whose fingerprint or screen lock works on this phone will " +
                                "be able to open the app, so leave it off if a child can unlock this phone. " +
                                "The passcode still works, and it's still what a kid's phone asks for.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = useDeviceAuth,
                        onCheckedChange = { wanted ->
                            val activity = context as? FragmentActivity
                            if (!wanted) {
                                lockPrefs.useDeviceAuth = false
                                useDeviceAuth = false
                            } else if (activity != null) {
                                // Prove it works (and that it's really the owner) before turning it on.
                                DeviceAuth.authenticate(activity, "Turn on unlock with fingerprint or screen lock", "Confirm it's you") { ok ->
                                    if (ok) {
                                        lockPrefs.useDeviceAuth = true
                                        useDeviceAuth = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("passcode_device_auth_toggle")
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = current,
                onValueChange = { current = it.filter(Char::isDigit) },
                label = { Text("New passcode (4-6 digits)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it.filter(Char::isDigit) },
                label = { Text("Confirm passcode") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (saved) {
                Spacer(Modifier.height(8.dp))
                Text("Saved.", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = current.length in 4..6 && !saving,
                onClick = {
                    if (current != confirm) {
                        error = "Passcodes don't match."
                        return@Button
                    }
                    error = null
                    saving = true
                    scope.launch {
                        // PBKDF2 is deliberately slow - keep it off the UI thread.
                        val (hash, salt) = withContext(Dispatchers.Default) {
                            val salt = PasscodeHasher.randomSalt()
                            PasscodeHasher.hash(current, salt) to salt
                        }
                        repository.setParentPasscode(parentUid, hash, salt)
                        hasPasscode = true
                        saved = true
                        saving = false
                        current = ""
                        confirm = ""
                    }
                }
            ) {
                Text(if (saving) "Saving..." else "Save passcode")
            }
        }
    }
}
