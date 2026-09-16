package org.openscreentime.kid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.openscreentime.kid.data.NotificationDigestStore
import org.openscreentime.shared.model.DigestAppGroup
import org.openscreentime.shared.model.DigestNotification
import org.openscreentime.shared.model.groupDigestByApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only, text-only digest of today's notifications on this device (see #20).
 * Tapping a row does nothing on purpose - this is not a launcher back into the source app.
 */
@Composable
fun NotificationDigestScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NotificationDigestStore(context) }
    var groups by remember { mutableStateOf(groupDigestByApp(store.entries)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                groups = groupDigestByApp(store.entries)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's notifications") },
                navigationIcon = {
                    TextButton(onClick = onDone, modifier = Modifier.testTag("digest_done")) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text(
                    "Nothing yet today. New notifications will show up here as a plain list, grouped by app.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("digest_empty")
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("digest_list")
            ) {
                items(groups, key = { it.packageName }) { group ->
                    DigestGroup(group)
                }
            }
        }
    }
}

@Composable
private fun DigestGroup(group: DigestAppGroup) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(group.appLabel, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        group.entries.forEach { entry ->
            DigestRow(entry)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DigestRow(entry: DigestNotification) {
    val time = remember(entry.postedAtMs) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(entry.postedAtMs))
    }
    val line = listOf(entry.title, entry.text).filter { it.isNotBlank() }.joinToString(" — ")
    Text(
        "$time  $line",
        style = MaterialTheme.typography.bodyMedium
    )
}
