package org.openscreentime.parent.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openscreentime.shared.model.PasscodeInfo
import org.openscreentime.shared.util.PasscodeHasher

private const val MAX_ATTEMPTS = 5

@Composable
fun PasscodeUnlockScreen(passcode: PasscodeInfo, onUnlocked: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    val locked = attempts >= MAX_ATTEMPTS

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
            enabled = entered.isNotBlank() && !locked,
            onClick = {
                if (PasscodeHasher.verify(entered, passcode.salt, passcode.hash)) {
                    onUnlocked()
                } else {
                    attempts++
                    entered = ""
                    error = if (attempts >= MAX_ATTEMPTS) "Too many incorrect attempts. Try again later." else "Incorrect passcode."
                }
            }
        ) {
            Text("Unlock")
        }
    }
}
