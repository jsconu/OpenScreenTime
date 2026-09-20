package org.openscreentime.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openscreentime.shared.model.AppSort

/** "Most used | A to Z" - switches how a per-app list is ordered. Usage order is the default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSortToggle(sort: AppSort, onChange: (AppSort) -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        FilterChip(
            selected = sort == AppSort.USAGE,
            onClick = { onChange(AppSort.USAGE) },
            label = { Text("Most used") },
            modifier = Modifier.testTag("sort_usage")
        )
        FilterChip(
            selected = sort == AppSort.NAME,
            onClick = { onChange(AppSort.NAME) },
            label = { Text("A to Z") },
            modifier = Modifier.testTag("sort_name")
        )
    }
}
