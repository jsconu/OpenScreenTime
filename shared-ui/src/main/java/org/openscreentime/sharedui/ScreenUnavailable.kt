package org.openscreentime.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * What a screen shows when the thing it needs isn't there yet (still loading) or any more (signed out, or a profile
 * that was removed). A screen must never just return with nothing drawn: that is a blank white page with no way out.
 * There is always a message and a way back or on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenUnavailable(
    message: String,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        TextButton(onClick = onBack, modifier = Modifier.testTag("unavailable_back")) { Text(backLabel) }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth().testTag("unavailable_action")
                ) { Text(actionLabel) }
            }
        }
    }
}
