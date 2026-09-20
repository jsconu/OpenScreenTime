package org.openscreentime.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.openscreentime.shared.model.describeBedtimeWindow

private const val DEFAULT_START_MINUTES = 21 * 60
private const val DEFAULT_END_MINUTES = 7 * 60

/**
 * Sets a bedtime in two plain steps, each with a 12-hour clock and an AM/PM switch: first when
 * bedtime STARTS (that evening or night), then when it ENDS (the next morning). A live sentence
 * underneath spells out the result - "9:00 PM tonight to 7:00 AM tomorrow morning (10 hours)" - so
 * nobody has to reason about a 24-hour clock or a window that crosses midnight. This is the same
 * shape Apple's Sleep schedule, Google's Bedtime mode and Family Link use: a start, an end, and a
 * summary of the night in between.
 *
 * [onConfirm] gets the start and end as minutes since midnight, or (null, null) to turn bedtime off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedtimeWindowDialog(
    initialStartMinutes: Int?,
    initialEndMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?, Int?) -> Unit
) {
    val hasExisting = initialStartMinutes != null && initialEndMinutes != null
    val start0 = initialStartMinutes ?: DEFAULT_START_MINUTES
    val end0 = initialEndMinutes ?: DEFAULT_END_MINUTES
    val startState = rememberTimePickerState(initialHour = start0 / 60, initialMinute = start0 % 60, is24Hour = false)
    val endState = rememberTimePickerState(initialHour = end0 / 60, initialMinute = end0 % 60, is24Hour = false)
    var step by remember { mutableIntStateOf(0) }

    val startMinutes = startState.hour * 60 + startState.minute
    val endMinutes = endState.hour * 60 + endState.minute
    val sameTime = startMinutes == endMinutes

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp),
        title = { Text(if (step == 0) "Bedtime starts" else "Bedtime ends") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (step == 0) {
                        "Step 1 of 2 - the evening or night apps lock. Pick the time and AM or PM (for example 9:00 PM)."
                    } else {
                        "Step 2 of 2 - the next morning, when apps unlock again. Pick the time and AM or PM (for example 7:00 AM)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                TimePicker(state = if (step == 0) startState else endState)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (sameTime) {
                        "Start and end can't be the same time."
                    } else {
                        describeBedtimeWindow(startMinutes, endMinutes)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (sameTime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().testTag("bedtime_summary")
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Blocks every app during this window, independent of the daily limit.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasExisting) {
                    TextButton(
                        onClick = { onConfirm(null, null) },
                        modifier = Modifier.testTag("bedtime_turn_off")
                    ) { Text("Turn bedtime off") }
                }
            }
        },
        confirmButton = {
            if (step == 0) {
                TextButton(onClick = { step = 1 }, modifier = Modifier.testTag("bedtime_next")) { Text("Next") }
            } else {
                TextButton(
                    enabled = !sameTime,
                    onClick = { onConfirm(startMinutes, endMinutes) },
                    modifier = Modifier.testTag("bedtime_save")
                ) { Text("Save") }
            }
        },
        dismissButton = {
            if (step == 0) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            } else {
                TextButton(onClick = { step = 0 }) { Text("Back") }
            }
        }
    )
}
