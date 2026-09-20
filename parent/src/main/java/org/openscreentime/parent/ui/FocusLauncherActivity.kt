package org.openscreentime.parent.ui

import android.content.ComponentName
import android.content.Context
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.data.NotificationDigestStore
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.shared.model.FocusProfile
import org.openscreentime.shared.util.FocusPrefs
import org.openscreentime.shared.util.buildAllApps
import org.openscreentime.shared.util.buildFocusHomeApps
import org.openscreentime.shared.util.setEnabledLocally
import org.openscreentime.sharedui.FocusBreathDialog
import org.openscreentime.sharedui.FocusHomeScreen
import org.openscreentime.sharedui.NotificationDigestScreen

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
        val prefs = FocusPrefs(this)
        val selfId = SelfProfileStore(this).childId
        val digestStore = NotificationDigestStore(this)

        setContent {
            OpenScreenTimeTheme {
                val scope = rememberCoroutineScope()
                var config by remember { mutableStateOf(prefs.config()) }
                var openUntil by remember { mutableStateOf(prefs.openUntilMs) }
                var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                var showBreath by remember { mutableStateOf(false) }
                var showCalm by remember { mutableStateOf(false) }

                // Pick up changes and expire the "all apps" window on time.
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
                        allAppsButtonLabel = "All apps",
                        showTravelSwitch = true,
                        travelOn = config.profile == FocusProfile.TRAVEL,
                        canOpenCalm = digestStore.optedIn,
                        onLaunch = { pkg ->
                            packageManager.getLaunchIntentForPackage(pkg)?.let {
                                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        onOpenCalm = { showCalm = true },
                        onRequestAllApps = { showBreath = true },
                        onCloseAllApps = { prefs.openUntilMs = null; openUntil = null },
                        onToggleTravel = { travel ->
                            val profile = if (travel) FocusProfile.TRAVEL else FocusProfile.STANDARD
                            val uid = repository.currentUid
                            if (uid != null && selfId != null) scope.launch { runCatching { repository.setFocusProfile(uid, selfId, profile) } }
                        },
                        onTurnOff = {
                            prefs.setEnabledLocally(this@FocusLauncherActivity, component(this@FocusLauncherActivity), false)
                            val uid = repository.currentUid
                            if (uid != null && selfId != null) scope.launch { runCatching { repository.setFocusMode(uid, selfId, false) } }
                            startActivity(Intent(this@FocusLauncherActivity, MainActivity::class.java))
                        }
                    )
                }

                if (showBreath) {
                    FocusBreathDialog(
                        minutes = ALL_APPS_MINUTES,
                        onStay = { showBreath = false },
                        onOpen = {
                            val until = System.currentTimeMillis() + ALL_APPS_MINUTES * 60_000L
                            prefs.openUntilMs = until
                            openUntil = until
                            showBreath = false
                        }
                    )
                }
            }
        }
    }

    companion object {
        /** How long "All apps" stays open on an adult's own phone. */
        const val ALL_APPS_MINUTES = 10

        fun component(context: Context) = ComponentName(context, FocusLauncherActivity::class.java)
    }
}
