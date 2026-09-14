package org.openscreentime.parent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.todayDateString
import org.openscreentime.shared.repo.FamilyRepository

@Composable
fun DashboardScreen(
    repository: FamilyRepository,
    onOpenChild: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    val parentUid = repository.currentUid ?: return
    val scope = rememberCoroutineScope()
    var children by remember { mutableStateOf<List<ChildProfile>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newChildCode by remember { mutableStateOf<String?>(null) }

    DisposableEffect(parentUid) {
        val reg = repository.listenChildren(parentUid) { children = it }
        onDispose { reg.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your children") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Passcode") }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Text("+") }
        }
    ) { padding ->
        if (children.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Add your first child to get started.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(children, key = { it.id }) { child ->
                    ChildSummaryCard(
                        repository = repository,
                        parentUid = parentUid,
                        child = child,
                        onClick = { onOpenChild(child.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddChildDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { name ->
                scope.launch {
                    val child = repository.createChild(parentUid, name)
                    newChildCode = child.pairingCode
                    showAddDialog = false
                }
            }
        )
    }

    newChildCode?.let { code ->
        AlertDialog(
            onDismissRequest = { newChildCode = null },
            title = { Text("Pairing code") },
            text = { Text("Enter this code in OpenScreenTime Kid on your child's phone:\n\n$code") },
            confirmButton = { TextButton(onClick = { newChildCode = null }) { Text("Done") } }
        )
    }
}

@Composable
private fun ChildSummaryCard(
    repository: FamilyRepository,
    parentUid: String,
    child: ChildProfile,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(DailyStats(date = todayDateString())) }
    var showLockConfirm by remember { mutableStateOf(false) }

    DisposableEffect(child.id) {
        val reg = repository.listenDailyStats(parentUid, child.id, todayDateString()) { stats = it }
        onDispose { reg.remove() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(child.name, style = MaterialTheme.typography.titleMedium)
                if (child.paired) {
                    OutlinedButton(
                        onClick = {
                            if (child.locked) {
                                scope.launch { repository.setLocked(parentUid, child.id, false) }
                            } else {
                                showLockConfirm = true
                            }
                        },
                        colors = if (child.locked) {
                            ButtonDefaults.outlinedButtonColors()
                        } else {
                            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        }
                    ) {
                        Text(if (child.locked) "Resume" else "Lock now")
                    }
                }
            }
            if (!child.paired) {
                Spacer(Modifier.height(4.dp))
                Text("Waiting for device pairing (code: ${child.pairingCode})", style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatColumn("Screen time today", formatDuration(stats.totalScreenTimeMs))
                    StatColumn("Unlocks", stats.unlockCount.toString())
                    StatColumn("Daily limit", "${child.dailyLimitMinutes} min")
                }
                if (child.locked) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Screen time is paused",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showLockConfirm) {
        AlertDialog(
            onDismissRequest = { showLockConfirm = false },
            title = { Text("End screen time now?") },
            text = { Text("This blocks every app on ${child.name}'s device until you resume it.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.setLocked(parentUid, child.id, true) }
                    showLockConfirm = false
                }) { Text("Lock now") }
            },
            dismissButton = { TextButton(onClick = { showLockConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AddChildDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a child") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Child's name") }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
