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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.data.PairingStore

@Composable
fun PairingScreen(onPaired: (name: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Pair this device", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask the parent for the 6-digit pairing code shown in their app.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
            label = { Text("Pairing code") },
            singleLine = true,
            modifier = Modifier.testTag("pairing_code_input")
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            enabled = code.length == 6 && !loading,
            modifier = Modifier.testTag("pairing_submit"),
            onClick = {
                loading = true
                error = null
                scope.launch {
                    try {
                        val repository = (context.applicationContext as KidApp).repository
                        val (parentUid, child) = repository.claimPairingCode(code)
                        val store = PairingStore(context)
                        store.parentUid = parentUid
                        store.childId = child.id
                        store.childName = child.name
                        onPaired(child.name)
                    } catch (e: Exception) {
                        error = e.message ?: "Pairing failed. Check the code and try again."
                    } finally {
                        loading = false
                    }
                }
            }
        ) {
            Text(if (loading) "Pairing..." else "Pair device")
        }
    }
}
