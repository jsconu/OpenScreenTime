package org.openscreentime.parent.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.shared.util.PasscodeHasher
import org.openscreentime.shared.repo.FamilyRepository

/**
 * Sets or changes the one family passcode: it locks this app, and can also be entered
 * on a paired kid device to unlock "parent mode" there (see ParentModeUnlockScreen in
 * the kid app).
 */
@Composable
fun PasscodeSettingsScreen(repository: FamilyRepository, onBack: () -> Unit) {
    val parentUid = repository.currentUid ?: return
    val scope = rememberCoroutineScope()

    var hasPasscode by remember { mutableStateOf<Boolean?>(null) }
    var current by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

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
