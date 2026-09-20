package org.openscreentime.sharedui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.openscreentime.shared.model.DigestNotification
import org.openscreentime.shared.model.FocusProfile
import org.openscreentime.shared.util.FocusMode

/** Where the calm notification list comes from, when the person has opted in to it. */
class FocusDigest(val optedIn: Boolean, val load: () -> List<DigestNotification>)

/**
 * How "everything else" gets opened, the one thing that differs between the two phones' dumb-phone home screens:
 * a child's phone asks a parent for the passcode, a parent's own phone asks for a breath first.
 *
 * [dialog] is shown once the person taps the button (and [available] says it can be used); it calls `onGranted` with
 * the time (epoch ms) until which every app is open, or `onDismiss`.
 */
class FocusUnlock(
    val buttonLabel: String,
    val available: () -> Boolean = { true },
    /** Shown instead of [dialog] when [available] is false, so the button never does nothing. */
    val unavailableMessage: String = "",
    val dialog: @Composable (onGranted: (untilMs: Long) -> Unit, onDismiss: () -> Unit) -> Unit
)

/**
 * The dumb-phone home screen, everything but the app-specific parts (see #42): polls the saved choice, expires the
 * "all apps" window on time, launches apps, and shows the calm list on a swipe down. The kid and parent launcher
 * activities are thin adapters that supply [unlock], [digest], and (for an adult's own phone) [onToggleTravel] and
 * [onTurnOff].
 */
@Composable
fun FocusLauncherScreen(
    focus: FocusMode,
    unlock: FocusUnlock,
    digest: FocusDigest?,
    onToggleTravel: ((Boolean) -> Unit)? = null,
    onTurnOff: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(focus.config) }
    var openUntil by remember { mutableStateOf(focus.openUntilMs) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showUnlock by remember { mutableStateOf(false) }
    var showCalm by remember { mutableStateOf(false) }
    var showUnavailable by remember { mutableStateOf(false) }

    // Pick up changes the parent makes, and expire the "all apps" window on time.
    LaunchedEffect(Unit) {
        while (true) {
            config = focus.config
            openUntil = focus.openUntilMs
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    BackHandler {}

    val windowOpen = openUntil?.let { it > nowMs } == true
    if (showCalm && digest != null) {
        NotificationDigestScreen(loadEntries = digest.load, onDone = { showCalm = false })
    } else {
        FocusHomeScreen(
            apps = focus.homeApps(config),
            allApps = if (windowOpen) focus.allApps() else null,
            allAppsMinutesLeft = openUntil?.let { (((it - nowMs) + 59_999L) / 60_000L).toInt() },
            allAppsButtonLabel = unlock.buttonLabel,
            showTravelSwitch = onToggleTravel != null,
            travelOn = config.profile == FocusProfile.TRAVEL,
            canOpenCalm = digest?.optedIn == true,
            onLaunch = { pkg ->
                context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            onOpenCalm = { showCalm = true },
            onRequestAllApps = {
                if (unlock.available()) showUnlock = true else showUnavailable = true
            },
            onCloseAllApps = { focus.openUntilMs = null; openUntil = null },
            onToggleTravel = { onToggleTravel?.invoke(it) },
            onTurnOff = onTurnOff
        )
    }

    if (showUnavailable) {
        AlertDialog(
            onDismissRequest = { showUnavailable = false },
            title = { Text("Ask a parent") },
            text = { Text(unlock.unavailableMessage) },
            confirmButton = { TextButton(onClick = { showUnavailable = false }) { Text("OK") } }
        )
    }

    if (showUnlock) {
        unlock.dialog(
            { until ->
                focus.openUntilMs = until
                openUntil = until
                showUnlock = false
            },
            { showUnlock = false }
        )
    }
}

/**
 * Whether this app is the phone's home screen right now, re-checked whenever the person comes back from the system
 * prompt or Settings (where they choose it).
 */
@Composable
fun rememberIsDefaultHome(focus: FocusMode): Boolean {
    var isHome by remember { mutableStateOf(focus.isDefaultHome()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isHome = focus.isDefaultHome()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return isHome
}
