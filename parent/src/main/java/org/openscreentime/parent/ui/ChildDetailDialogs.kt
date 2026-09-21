package org.openscreentime.parent.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.openscreentime.shared.model.AppList
import org.openscreentime.shared.model.AppUsage
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.sharedui.AppPickerDialog
import org.openscreentime.sharedui.BedtimeWindowDialog
import org.openscreentime.sharedui.MinutesInputDialog
import org.openscreentime.sharedui.UnlockGoalInputDialog
import org.openscreentime.sharedui.pickerTitle

/** The one dialog open over a person's page, if any. A new setting adds a case here and a branch below. */
internal sealed interface DetailDialog {
    data object DailyLimit : DetailDialog
    data object UnlockGoal : DetailDialog
    data object Bedtime : DetailDialog
    data object FrequentCheck : DetailDialog
    data object LockConfirm : DetailDialog
    data object DeleteConfirm : DetailDialog
    data class AppLimit(val packageName: String) : DetailDialog
    data class AppPicker(val list: AppList) : DetailDialog
}

/**
 * Renders whichever [DetailDialog] is open over a person's page and applies what it confirms. [onClose] closes it;
 * [onOpenReportAnyway] and [onChildRemoved] hand back to the screen.
 */
@Composable
internal fun ChildDetailDialogs(
    dialog: DetailDialog?,
    onClose: () -> Unit,
    child: ChildProfile,
    apps: List<AppUsage>,
    repository: FamilyRepository,
    parentUid: String,
    childId: String,
    /**
     * Must outlive this host: deleting a child removes the page's data, which takes this host off the screen before
     * the delete's "now go back" step runs. So the screen's scope is passed in rather than made here.
     */
    scope: CoroutineScope,
    onOpenReportAnyway: () -> Unit,
    onChildRemoved: () -> Unit
) {
    when (dialog) {
        null -> Unit
        DetailDialog.DailyLimit -> MinutesInputDialog(
            title = "Daily screen time limit",
            initialMinutes = child.dailyLimitMinutes,
            onDismiss = onClose,
            onConfirm = { minutes ->
                scope.launch { repository.updateDailyLimit(parentUid, childId, minutes) }
                onClose()
            }
        )
        DetailDialog.UnlockGoal -> UnlockGoalInputDialog(
            initialGoal = child.dailyUnlockGoal,
            onDismiss = onClose,
            onConfirm = { goal ->
                scope.launch { repository.updateDailyUnlockGoal(parentUid, childId, goal) }
                onClose()
            }
        )
        DetailDialog.Bedtime -> BedtimeWindowDialog(
            initialStartMinutes = child.bedtimeStartMinutes,
            initialEndMinutes = child.bedtimeEndMinutes,
            onDismiss = onClose,
            onConfirm = { start, end ->
                scope.launch { repository.updateBedtimeWindow(parentUid, childId, start, end) }
                onClose()
            }
        )
        DetailDialog.FrequentCheck -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Check in, not check up") },
            text = {
                Text(
                    "You've opened a report a few times today already. A quick look now and " +
                        "then makes sense, but checking constantly can turn this into its own " +
                        "source of stress. Still want to open it?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClose()
                    onOpenReportAnyway()
                }) { Text("Open anyway") }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("Not now") } }
        )
        DetailDialog.LockConfirm -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("End screen time now?") },
            text = { Text("This blocks every app on ${child.name}'s device until you resume it.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.setLocked(parentUid, childId, true) }
                    onClose()
                }) { Text("End now") }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
        )
        DetailDialog.DeleteConfirm -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Remove ${child.name}?") },
            text = {
                Text(
                    "This deletes ${child.name}'s profile and all of their screen time history. " +
                        "The kid app will need to be unpaired and re-paired to track again. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteChild(parentUid, childId)
                        onChildRemoved()
                    }
                    onClose()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
        )
        is DetailDialog.AppLimit -> {
            val pkg = dialog.packageName
            val appName = apps.firstOrNull { it.packageName == pkg }?.appName ?: pkg
            MinutesInputDialog(
                title = "Daily limit for $appName",
                initialMinutes = child.appLimits[pkg] ?: 60,
                onDismiss = onClose,
                onConfirm = { minutes ->
                    scope.launch { repository.setAppLimit(parentUid, childId, pkg, minutes) }
                    onClose()
                }
            )
        }
        is DetailDialog.AppPicker -> AppPickerDialog(
            title = dialog.list.pickerTitle,
            apps = apps,
            selected = child.packages(dialog.list).toSet(),
            onToggle = { pkg, member ->
                scope.launch { repository.setAppListMember(parentUid, childId, dialog.list, pkg, member) }
            },
            onDone = onClose
        )
    }
}
