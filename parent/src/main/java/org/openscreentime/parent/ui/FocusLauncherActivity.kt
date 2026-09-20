package org.openscreentime.parent.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.data.NotificationDigestStore
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.shared.model.FocusProfile
import org.openscreentime.shared.util.FocusMode
import org.openscreentime.sharedui.FocusBreathDialog
import org.openscreentime.sharedui.FocusDigest
import org.openscreentime.sharedui.FocusLauncherScreen
import org.openscreentime.sharedui.FocusUnlock

/**
 * The parent's own "dumb phone" home screen (see #42): the phone, texting, sign-in-code and chosen apps as plain
 * text, with a way to reach everything else. It stays switched off until Focus mode is turned on for the parent's
 * own phone, and only takes effect once it's set as the phone's home app. From here an adult can:
 *  - tap "All apps" (after a short breath) to open every app for 10 minutes, then it goes back on its own;
 *  - flip Travel mode, which also lets through tickets, maps and the like;
 *  - swipe down from the top for the calm notification list;
 *  - turn the dumb phone off entirely.
 * OpenScreenTime itself is always in the list.
 */
class FocusLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as ParentApp).repository
        val focus = focusMode(this)
        val selfId = SelfProfileStore(this).childId
        val digestStore = NotificationDigestStore(this)

        setContent {
            OpenScreenTimeTheme {
                val scope = rememberCoroutineScope()
                fun launchForSelf(block: suspend (parentUid: String, childId: String) -> Unit) {
                    val uid = repository.currentUid
                    if (uid != null && selfId != null) scope.launch { runCatching { block(uid, selfId) } }
                }

                FocusLauncherScreen(
                    focus = focus,
                    unlock = FocusUnlock(
                        buttonLabel = "All apps",
                        dialog = { onGranted, onDismiss ->
                            FocusBreathDialog(
                                minutes = ALL_APPS_MINUTES,
                                onStay = onDismiss,
                                onOpen = { onGranted(System.currentTimeMillis() + ALL_APPS_MINUTES * 60_000L) }
                            )
                        }
                    ),
                    digest = FocusDigest(digestStore.optedIn) { digestStore.entries },
                    onToggleTravel = { travel ->
                        val profile = if (travel) FocusProfile.TRAVEL else FocusProfile.STANDARD
                        focus.setProfileHere(profile)
                        launchForSelf { uid, id -> repository.setFocusProfile(uid, id, profile) }
                    },
                    onTurnOff = {
                        focus.turnOffHere()
                        launchForSelf { uid, id -> repository.setFocusMode(uid, id, false) }
                        startActivity(Intent(this@FocusLauncherActivity, MainActivity::class.java))
                    }
                )
            }
        }
    }

    companion object {
        /** How long "All apps" stays open on an adult's own phone. */
        const val ALL_APPS_MINUTES = 10

        fun component(context: Context) = ComponentName(context, FocusLauncherActivity::class.java)

        /** This phone's Focus mode, tied to this launcher. */
        fun focusMode(context: Context) = FocusMode(context, component(context))
    }
}
