package org.openscreentime.kid.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.data.NotificationDigestStore
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.monitor.LiveChildState
import org.openscreentime.shared.model.relockAtFor
import org.openscreentime.shared.util.FocusPrefs
import org.openscreentime.shared.util.PasscodeAttemptStore
import org.openscreentime.shared.util.PasscodeHasher
import org.openscreentime.shared.util.buildAllApps
import org.openscreentime.shared.util.buildFocusHomeApps
import org.openscreentime.sharedui.FocusHomeScreen
import org.openscreentime.sharedui.NotificationDigestScreen
import org.openscreentime.sharedui.ParentUnlockDialog

/**
 * The child's "dumb phone" home screen (see #42): the phone, texting, sign-in-code and parent-chosen apps only.
 * It is switched off until a parent turns Focus mode on for this child, and only takes effect once it's set as the
 * phone's home app (Settings > Home screen). A parent can open everything for a while with the family passcode.
 */
class FocusLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = FocusPrefs(this)
        val pairing = PairingStore(this)
        val repository = (application as KidApp).repository
        val digestStore = NotificationDigestStore(this)

        setContent {
            OpenScreenTimeTheme {
                val scope = rememberCoroutineScope()
                var config by remember { mutableStateOf(prefs.config()) }
                var openUntil by remember { mutableStateOf(prefs.openUntilMs) }
                var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                var showUnlock by remember { mutableStateOf(false) }
                var showCalm by remember { mutableStateOf(false) }
                var unlockError by remember { mutableStateOf<String?>(null) }
                var verifying by remember { mutableStateOf(false) }

                // Pick up changes the parent makes, and expire the "all apps" window on time.
                LaunchedEffect(Unit) {
                    while (true) {
                        config = prefs.config()
                        openUntil = prefs.openUntilMs
                        nowMs = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                BackHandler {}

                val windowOpen = openUntil?.let { it > nowMs } == true
                if (showCalm) {
                    NotificationDigestScreen(loadEntries = { digestStore.entries }, onDone = { showCalm = false })
                } else {
                    FocusHomeScreen(
                        apps = buildFocusHomeApps(this@FocusLauncherActivity, config),
                        allApps = if (windowOpen) buildAllApps(this@FocusLauncherActivity) else null,
                        allAppsMinutesLeft = openUntil?.let { (((it - nowMs) + 59_999L) / 60_000L).toInt() },
                        allAppsButtonLabel = "Parent unlock",
                        showTravelSwitch = false,
                        travelOn = false,
                        canOpenCalm = digestStore.optedIn,
                        onLaunch = { pkg ->
                            packageManager.getLaunchIntentForPackage(pkg)?.let {
                                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        onOpenCalm = { showCalm = true },
                        onRequestAllApps = {
                            unlockError = null
                            showUnlock = LiveChildState.parentPasscodeHash != null
                        },
                        onCloseAllApps = { prefs.openUntilMs = null; openUntil = null },
                        onToggleTravel = {},
                        onTurnOff = null
                    )
                }

                if (showUnlock) {
                    ParentUnlockDialog(
                        verifying = verifying,
                        error = unlockError,
                        onDismiss = { showUnlock = false },
                        onSubmit = { pin, minutes ->
                            val attempts = PasscodeAttemptStore(this@FocusLauncherActivity)
                            val hash = LiveChildState.parentPasscodeHash
                            val salt = LiveChildState.parentPasscodeSalt
                            if (attempts.isLocked()) {
                                unlockError = "Too many incorrect attempts. Try again in ${attempts.minutesRemaining()} minutes."
                            } else if (hash == null || salt == null) {
                                unlockError = "No family passcode is set yet."
                            } else {
                                verifying = true
                                scope.launch {
                                    val ok = withContext(Dispatchers.Default) { PasscodeHasher.verify(pin, salt, hash) }
                                    verifying = false
                                    if (ok) {
                                        attempts.recordSuccess()
                                        // "Until I lock it again" has no meaning here: a long window instead (12 hours).
                                        val until = relockAtFor(minutes ?: (12 * 60), System.currentTimeMillis())
                                        prefs.openUntilMs = until
                                        openUntil = until
                                        showUnlock = false
                                    } else {
                                        unlockError = if (attempts.recordFailure()) {
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
            }
        }
    }

    companion object {
        fun component(context: android.content.Context) = ComponentName(context, FocusLauncherActivity::class.java)
    }
}
