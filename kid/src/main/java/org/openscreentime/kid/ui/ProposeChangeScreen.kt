package org.openscreentime.kid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.shared.model.AppUsage
import org.openscreentime.shared.model.mergeUsageWithInstalled
import org.openscreentime.shared.util.listLaunchableApps
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.sharedui.MinutesInputDialog

/**
 * Reachable directly from the Status screen with no passcode - unlike [ParentControlsScreen],
 * this is a request, not an override, so there's nothing here to gate. See #14: negotiated
 * limits are the autonomy-supportive counterpart to a parent unilaterally setting limits.
 */
@Composable
fun ProposeChangeScreen(
    repository: FamilyRepository,
    parentUid: String,
    childId: String,
    child: ChildProfile,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val usageStore = remember { UsageStore(context) }

    // Every app on the phone, not only ones already used today - so a limit can be suggested for any.
    val appUsage = remember {
        mergeUsageWithInstalled(
            usage = usageStore.appUsageMs.map { (pkg, ms) ->
                AppUsage(packageName = pkg, appName = usageStore.appNames[pkg] ?: pkg, foregroundTimeMs = ms)
            },
            installed = listLaunchableApps(context)
        )
    }

    var showLimitDialog by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<String?>(null) }

    val hasPendingProposal = child.proposedDailyLimitMinutes != null || child.proposedAppLimits != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suggest a change") },
                navigationIcon = { TextButton(onClick = onDone) { Text("Done") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Suggest a new limit. A parent will need to approve it before it changes " +
                            "anything - no passcode needed to send a suggestion.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (hasPendingProposal) {
                        Spacer(Modifier.height(12.dp))
                        Card {
                            Column(Modifier.padding(12.dp)) {
                                Text("Waiting for a parent to review", style = MaterialTheme.typography.labelLarge)
                                child.proposedDailyLimitMinutes?.let {
                                    Text("Suggested daily limit: $it min", style = MaterialTheme.typography.bodySmall)
                                }
                                if (child.proposedAppLimits != null) {
                                    Text("Includes suggested app limits", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Daily limit: ${child.dailyLimitMinutes} min", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showLimitDialog = true }) { Text("Suggest a new daily limit") }
                }
            }
            item {
                Text(
                    "App limits",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (appUsage.isEmpty()) {
                item {
                    Text(
                        "No apps found on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(appUsage, key = { it.packageName }) { app ->
                ListItem(
                    headlineContent = { Text(app.appName) },
                    supportingContent = {
                        val limit = (child.proposedAppLimits ?: child.appLimits)[app.packageName]
                        Text(if (limit != null) "Limit: $limit min/day" else "No limit set")
                    },
                    trailingContent = {
                        TextButton(onClick = { editingApp = app.packageName }) { Text("Suggest") }
                    }
                )
            }
        }
    }

    if (showLimitDialog) {
        MinutesInputDialog(
            title = "Suggest a new daily limit",
            initialMinutes = child.proposedDailyLimitMinutes ?: child.dailyLimitMinutes,
            onDismiss = { showLimitDialog = false },
            onConfirm = { minutes ->
                scope.launch { repository.proposeLimits(parentUid, childId, proposedDailyLimitMinutes = minutes) }
                showLimitDialog = false
            },
            confirmLabel = "Send suggestion"
        )
    }

    editingApp?.let { pkg ->
        val appName = appUsage.firstOrNull { it.packageName == pkg }?.appName ?: pkg
        MinutesInputDialog(
            title = "Suggest a limit for $appName",
            initialMinutes = (child.proposedAppLimits ?: child.appLimits)[pkg] ?: 60,
            onDismiss = { editingApp = null },
            onConfirm = { minutes ->
                scope.launch {
                    val base = child.proposedAppLimits ?: child.appLimits
                    val updated = base.toMutableMap().apply { put(pkg, minutes) }
                    repository.proposeLimits(parentUid, childId, proposedAppLimits = updated)
                }
                editingApp = null
            },
            confirmLabel = "Send suggestion"
        )
    }
}
