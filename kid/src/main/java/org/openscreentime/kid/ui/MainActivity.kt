package org.openscreentime.kid.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.data.AppearancePrefs
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.monitor.ScreenMonitorService
import org.openscreentime.kid.util.checkPermissions
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.computeStreak

private enum class KidScreen { STATUS, PARENT_UNLOCK, PARENT_CONTROLS, PROPOSE_CHANGE }

class MainActivity : ComponentActivity() {

    private lateinit var pairingStore: PairingStore

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingStore = PairingStore(this)
        val repository = (application as KidApp).repository
        val appearancePrefs = AppearancePrefs(this)

        setContent {
            var themeMode by remember { mutableStateOf(appearancePrefs.themeMode) }
            var textSize by remember { mutableStateOf(appearancePrefs.textSize) }

            OpenScreenTimeTheme(themeMode = themeMode, textSize = textSize) {
              // Lets UiAutomator (used by the :e2e module) match Modifier.testTag(...) as a
              // resource-id, since it can't drive Compose's own semantics tree directly.
              Box(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                var paired by remember { mutableStateOf(pairingStore.isPaired) }
                var permissions by remember { mutableStateOf(checkPermissions(this@MainActivity)) }
                var screen by remember { mutableStateOf(KidScreen.STATUS) }
                var child by remember { mutableStateOf<ChildProfile?>(null) }
                var streakDays by remember { mutableIntStateOf(0) }

                LaunchedEffect(pairingStore.parentUid, pairingStore.childId, child?.dailyLimitMinutes, child?.dailyUnlockGoal) {
                    val parentUid = pairingStore.parentUid
                    val childId = pairingStore.childId
                    val currentChild = child
                    if (parentUid != null && childId != null && currentChild != null) {
                        val recent = repository.getRecentDailyStats(parentUid, childId, STREAK_LOOKBACK_DAYS)
                        streakDays = computeStreak(recent, currentChild.dailyLimitMinutes, currentChild.dailyUnlockGoal)
                    }
                }

                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissions = checkPermissions(this@MainActivity)
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                DisposableEffect(paired) {
                    val parentUid = pairingStore.parentUid
                    val childId = pairingStore.childId
                    if (!paired || parentUid == null || childId == null) {
                        return@DisposableEffect onDispose {}
                    }
                    val reg = repository.listenChild(parentUid, childId) { child = it }
                    onDispose { reg.remove() }
                }

                if (!paired) {
                    PairingScreen(
                        onPaired = { name ->
                            pairingStore.childName = name
                            paired = true
                            ContextCompat.startForegroundService(
                                this@MainActivity,
                                Intent(this@MainActivity, ScreenMonitorService::class.java)
                            )
                            (application as KidApp).startLimitsListener()
                        }
                    )
                } else {
                    when (screen) {
                        KidScreen.STATUS -> StatusScreen(
                            childName = pairingStore.childName ?: "",
                            permissions = permissions,
                            themeMode = themeMode,
                            textSize = textSize,
                            streakDays = streakDays,
                            onRequestOverlay = {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                )
                            },
                            onRequestAccessibility = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onRequestBatteryExemption = {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:$packageName")
                                    )
                                )
                            },
                            onCycleTheme = { themeMode = appearancePrefs.cycleThemeMode() },
                            onCycleTextSize = { textSize = appearancePrefs.cycleTextSize() },
                            onOpenColorSettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onOpenParentMode = { screen = KidScreen.PARENT_UNLOCK },
                            onProposeChange = { screen = KidScreen.PROPOSE_CHANGE },
                            onUnpair = {
                                pairingStore.clear()
                                paired = false
                            }
                        )
                        KidScreen.PARENT_UNLOCK -> ParentModeUnlockScreen(
                            child = child,
                            onUnlocked = { screen = KidScreen.PARENT_CONTROLS },
                            onCancel = { screen = KidScreen.STATUS }
                        )
                        KidScreen.PARENT_CONTROLS -> {
                            val currentChild = child
                            val parentUid = pairingStore.parentUid
                            val childId = pairingStore.childId
                            if (currentChild != null && parentUid != null && childId != null) {
                                ParentControlsScreen(
                                    repository = repository,
                                    parentUid = parentUid,
                                    childId = childId,
                                    child = currentChild,
                                    onDone = { screen = KidScreen.STATUS }
                                )
                            }
                        }
                        KidScreen.PROPOSE_CHANGE -> {
                            val currentChild = child
                            val parentUid = pairingStore.parentUid
                            val childId = pairingStore.childId
                            if (currentChild != null && parentUid != null && childId != null) {
                                ProposeChangeScreen(
                                    repository = repository,
                                    parentUid = parentUid,
                                    childId = childId,
                                    child = currentChild,
                                    onDone = { screen = KidScreen.STATUS }
                                )
                            }
                        }
                    }
                }
              }
            }
        }
    }

    companion object {
        /** Look back far enough that a broken streak's reset is obvious, without querying forever. */
        private const val STREAK_LOOKBACK_DAYS = 14
    }
}
