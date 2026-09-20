package org.openscreentime.sharedui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openscreentime.shared.model.ChildProfile

/**
 * "Time that doesn't count": apps whose time is left out of the overall daily limit, for an adult or a child.
 * Their time still shows in the app list, and their own per-app limit still applies.
 */
fun LazyListScope.excludedFromTotalSection(child: ChildProfile, onPickApps: () -> Unit) {
    item {
        Column(Modifier.padding(16.dp)) {
            Text("Time that doesn't count", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose apps whose time isn't added to the daily limit, like an audiobook or reading app, maps or a " +
                    "school app. They still show in the app list, and their own limit still applies. To keep one " +
                    "usable after the daily limit is reached, also tick Always allow.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPickApps,
                modifier = Modifier.fillMaxWidth().testTag("excluded_pick_apps")
            ) { Text("Choose apps (${child.excludedFromTotalPackages.size})") }
        }
    }
}
