package org.openscreentime.sharedui

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics

/**
 * Makes a whole "label + switch" row one control: tapping the words toggles it, and a screen reader reads the label
 * together with the state ("Keep my phone simple, switch, on") instead of an unnamed switch. Give the [Switch]
 * itself `onCheckedChange = null`.
 */
fun Modifier.switchRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit): Modifier =
    toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)

/** The same for a "label + checkbox" row. Give the checkbox `onCheckedChange = null`. */
fun Modifier.checkboxRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit): Modifier =
    toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)

/** The same for a "label + radio button" row. Give the radio button `onClick = null`. */
fun Modifier.radioRow(selected: Boolean, onSelect: () -> Unit): Modifier =
    selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)

/**
 * For a row whose switch handler is too involved to hoist: reads the label and the switch as one item for a screen
 * reader. (The words don't toggle it by touch; prefer [switchRow] where the handler is simple.)
 */
fun Modifier.mergedRow(): Modifier = semantics(mergeDescendants = true) {}
