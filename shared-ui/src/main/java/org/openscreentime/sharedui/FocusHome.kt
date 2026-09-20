package org.openscreentime.sharedui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.openscreentime.shared.model.FocusApp
import java.text.DateFormat
import java.util.Date

/**
 * The "dumb phone" home screen (see #42): the time, then only the phone, texting, sign-in-code and chosen apps as
 * plain text. Swiping down from the top opens the calm notification list. An adult on their own phone can open
 * every other app for a while ("All apps", after a breath); on a child's phone that button is "Parent unlock".
 *
 * [allApps] is non-null while an "all apps" window is open; then the list shows everything, with a banner that
 * says how long is left and a button to close it early.
 */
@Composable
fun FocusHomeScreen(
    apps: List<FocusApp>,
    allApps: List<FocusApp>?,
    allAppsMinutesLeft: Int?,
    allAppsButtonLabel: String,
    showTravelSwitch: Boolean,
    travelOn: Boolean,
    canOpenCalm: Boolean,
    onLaunch: (String) -> Unit,
    onOpenCalm: () -> Unit,
    onRequestAllApps: () -> Unit,
    onCloseAllApps: () -> Unit,
    onToggleTravel: (Boolean) -> Unit,
    onTurnOff: (() -> Unit)?
) {
    val time by produceState(currentTimeText()) {
        while (true) {
            value = currentTimeText()
            delay(15_000)
        }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        // targetSdk 35 draws behind the system bars, so keep the clock and buttons clear of them.
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 24.dp)) {
            // The top of the screen: a swipe down here opens the calm notification list.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(canOpenCalm) {
                        if (!canOpenCalm) return@pointerInput
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 90f) onOpenCalm() },
                            onDragCancel = { total = 0f }
                        ) { _, dy -> total += dy }
                    }
                    .testTag("focus_top")
            ) {
                Text(time, style = MaterialTheme.typography.displayMedium)
                Text(
                    DateFormat.getDateInstance(DateFormat.FULL).format(Date()),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (canOpenCalm) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Swipe down for calm notifications",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClick = onOpenCalm)
                            .testTag("focus_calm_hint")
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            if (allApps != null) {
                Text(
                    "All apps are open for ${allAppsMinutesLeft ?: 0} more min",
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(onClick = onCloseAllApps, modifier = Modifier.testTag("focus_close_all_apps")) {
                    Text("Close and go back to focus")
                }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(allApps ?: apps, key = { it.packageName }) { app ->
                    Text(
                        app.label,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onLaunch(app.packageName) }
                            .padding(vertical = 10.dp)
                    )
                }
            }

            if (allApps == null) {
                if (showTravelSwitch) {
                    Row(
                        modifier = Modifier.fillMaxWidth().switchRow(travelOn, onToggleTravel),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Travel mode", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Also lets through tickets, maps and the like.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = travelOn, onCheckedChange = null, modifier = Modifier.testTag("focus_travel"))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = onRequestAllApps,
                    modifier = Modifier.fillMaxWidth().testTag("focus_all_apps")
                ) { Text(allAppsButtonLabel) }
                if (onTurnOff != null) {
                    TextButton(onClick = onTurnOff, modifier = Modifier.testTag("focus_turn_off")) {
                        Text("Turn off dumb phone")
                    }
                }
            }
        }
    }
}

private fun currentTimeText(): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())

/** A short pause before opening every app on your own phone: a chance to notice the reach and change your mind. */
@Composable
fun FocusBreathDialog(minutes: Int, onOpen: () -> Unit, onStay: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
    }
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text("Take a breath") },
        text = {
            Text(
                "You set this phone to keep things simple. Do you need another app right now? " +
                    "Opening all apps lasts $minutes minutes, then it goes back to focus on its own."
            )
        },
        confirmButton = {
            Button(
                enabled = secondsLeft == 0,
                onClick = onOpen,
                modifier = Modifier.testTag("focus_breath_open")
            ) { Text(if (secondsLeft == 0) "Open all apps" else "Open all apps ($secondsLeft)") }
        },
        dismissButton = { TextButton(onClick = onStay) { Text("Stay focused") } }
    )
}
