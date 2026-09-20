package org.openscreentime.kid.ui

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.kid.data.NotificationDigestStore
import org.openscreentime.kid.monitor.LiveChildState
import org.openscreentime.shared.model.relockAtFor
import org.openscreentime.shared.util.FocusMode
import org.openscreentime.shared.util.PasscodeAttemptStore
import org.openscreentime.shared.util.PasscodeHasher
import org.openscreentime.sharedui.FocusDigest
import org.openscreentime.sharedui.FocusLauncherScreen
import org.openscreentime.sharedui.FocusUnlock
import org.openscreentime.sharedui.ParentUnlockDialog

/**
 * The child's "dumb phone" home screen (see #42): the phone, texting, sign-in-code and parent-chosen apps only.
 * It is switched off until a parent turns Focus mode on for this child, and only takes effect once it's set as the
 * phone's home app (Settings > Home screen). A parent can open everything for a while with the family passcode.
 */
class FocusLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val focus = focusMode(this)
        val digestStore = NotificationDigestStore(this)

        setContent {
            OpenScreenTimeTheme {
                FocusLauncherScreen(
                    focus = focus,
                    unlock = FocusUnlock(
                        buttonLabel = "Parent unlock",
                        available = { LiveChildState.parentPasscodeHash != null },
                        unavailableMessage = "A parent hasn't set the family passcode yet, so other apps can't be " +
                            "opened from here. Ask them to set it in the parent app, or to turn off the simple phone.",
                        dialog = { onGranted, onDismiss -> ParentPasscodeUnlock(onGranted, onDismiss) }
                    ),
                    digest = FocusDigest(digestStore.optedIn) { digestStore.entries }
                )
            }
        }
    }

    companion object {
        fun component(context: Context) = ComponentName(context, FocusLauncherActivity::class.java)

        /** This phone's Focus mode, tied to this launcher. */
        fun focusMode(context: Context) = FocusMode(context, component(context))
    }
}

/** Checks the family passcode (with the shared lockout) and grants an "all apps" window of the chosen length. */
@Composable
private fun ParentPasscodeUnlock(onGranted: (untilMs: Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }

    ParentUnlockDialog(
        verifying = verifying,
        error = error,
        onDismiss = onDismiss,
        onSubmit = { pin, minutes ->
            val attempts = PasscodeAttemptStore(context)
            val hash = LiveChildState.parentPasscodeHash
            val salt = LiveChildState.parentPasscodeSalt
            if (attempts.isLocked()) {
                error = "Too many incorrect attempts. Try again in ${attempts.minutesRemaining()} minutes."
            } else if (hash == null || salt == null) {
                error = "No family passcode is set yet."
            } else {
                verifying = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { PasscodeHasher.verify(pin, salt, hash) }
                    verifying = false
                    if (ok) {
                        attempts.recordSuccess()
                        // "Until I lock it again" has no meaning here: a long window instead (12 hours).
                        val now = System.currentTimeMillis()
                        onGranted(relockAtFor(minutes ?: (12 * 60), now) ?: (now + 12 * 60 * 60_000L))
                    } else {
                        error = if (attempts.recordFailure()) {
                            "Too many incorrect attempts. Try again in ${attempts.minutesRemaining()} minutes."
                        } else {
                            "Incorrect passcode."
                        }
                    }
                }
            }
        }
    )
}
