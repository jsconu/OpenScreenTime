package org.openscreentime.parent.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.openscreentime.parent.AppLockState
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.data.AppearancePrefs
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.parent.monitor.ScreenMonitorService
import org.openscreentime.parent.util.checkPermissions
import org.openscreentime.shared.model.PasscodeInfo
import org.openscreentime.shared.model.randomTip

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as ParentApp).repository
        val appearancePrefs = AppearancePrefs(this)
        val selfProfileStore = SelfProfileStore(this)

        setContent {
            var themeMode by remember { mutableStateOf(appearancePrefs.themeMode) }
            var textSize by remember { mutableStateOf(appearancePrefs.textSize) }
            var selfPermissions by remember { mutableStateOf(checkPermissions(this)) }
            var isSelfTracking by remember { mutableStateOf(selfProfileStore.isTracking) }
            // Hoisted above NavHost so it survives navigating to a child/settings screen
            // and back - a fresh tip only on the next process start, not every revisit.
            val tip = remember { randomTip() }
            var tipAcknowledged by remember { mutableStateOf(false) }
            var tipDismissed by remember { mutableStateOf(false) }

            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        selfPermissions = checkPermissions(this@MainActivity)
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            OpenScreenTimeTheme(themeMode = themeMode, textSize = textSize) {
              // Lets UiAutomator (used by the :e2e module) match Modifier.testTag(...) as a
              // resource-id, since it can't drive Compose's own semantics tree directly.
              Box(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                val navController = rememberNavController()
                var signedIn by remember { mutableStateOf(repository.currentUid != null) }

                if (!signedIn) {
                    AuthScreen(repository = repository, onSignedIn = { signedIn = true })
                } else {
                    var passcodeChecked by remember { mutableStateOf(false) }
                    var passcode by remember { mutableStateOf<PasscodeInfo?>(null) }
                    var locked by remember { mutableStateOf(!AppLockState.unlockedThisSession) }

                    LaunchedEffect(signedIn) {
                        passcode = repository.getParentPasscode(repository.currentUid!!)
                        if (passcode == null) {
                            AppLockState.unlockedThisSession = true
                            locked = false
                        }
                        passcodeChecked = true
                    }

                    val currentPasscode = passcode
                    if (passcodeChecked && locked && currentPasscode != null) {
                        PasscodeUnlockScreen(
                            passcode = currentPasscode,
                            onUnlocked = {
                                AppLockState.unlockedThisSession = true
                                locked = false
                            }
                        )
                    } else if (passcodeChecked) {
                        NavHost(navController = navController, startDestination = "dashboard") {
                            composable("dashboard") {
                                DashboardScreen(
                                    repository = repository,
                                    tip = tip,
                                    tipAcknowledged = tipAcknowledged,
                                    tipDismissed = tipDismissed,
                                    onTipAcknowledge = { tipAcknowledged = true },
                                    onTipDismiss = { tipDismissed = true },
                                    onOpenChild = { childId -> navController.navigate("child/$childId") },
                                    onOpenSettings = { navController.navigate("settings") },
                                    onOpenAppearance = { navController.navigate("appearance") },
                                    onOpenSelfTracking = { navController.navigate("self") },
                                    onSignOut = {
                                        repository.signOut()
                                        AppLockState.unlockedThisSession = false
                                        signedIn = false
                                    }
                                )
                            }
                            composable(
                                "child/{childId}",
                                arguments = listOf(navArgument("childId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val childId = backStackEntry.arguments?.getString("childId")!!
                                ChildDetailScreen(
                                    repository = repository,
                                    childId = childId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("settings") {
                                PasscodeSettingsScreen(
                                    repository = repository,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("appearance") {
                                AppearanceSettingsScreen(
                                    prefs = appearancePrefs,
                                    onThemeModeChanged = { themeMode = it },
                                    onTextSizeChanged = { textSize = it },
                                    onOpenColorSettings = {
                                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("self") {
                                SelfTrackingScreen(
                                    isTracking = isSelfTracking,
                                    permissions = selfPermissions,
                                    onStartTracking = {
                                        val self = repository.getOrCreateSelfProfile(repository.currentUid!!, "Me")
                                        selfProfileStore.childId = self.id
                                        isSelfTracking = true
                                        ContextCompat.startForegroundService(
                                            this@MainActivity,
                                            Intent(this@MainActivity, ScreenMonitorService::class.java)
                                        )
                                        (application as ParentApp).startSelfTrackingListener()
                                    },
                                    onStopTracking = {
                                        selfProfileStore.clear()
                                        isSelfTracking = false
                                        navController.popBackStack()
                                    },
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
                                    onViewMyStats = {
                                        selfProfileStore.childId?.let { navController.navigate("child/$it") }
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
              }
            }
        }
    }
}
