package org.openscreentime.parent.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
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
import org.openscreentime.parent.data.NotificationDigestStore
import org.openscreentime.parent.data.AppearancePrefs
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.parent.monitor.ScreenMonitorService
import org.openscreentime.parent.util.checkPermissions
import org.openscreentime.shared.model.HelpAudience
import org.openscreentime.shared.model.PasscodeInfo
import org.openscreentime.sharedui.AccessibilityDisclosureDialog
import org.openscreentime.sharedui.HelpBotScreen
import org.openscreentime.sharedui.NotificationDigestScreen

class MainActivity : FragmentActivity() {

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
                var showAccessibilityDisclosure by remember { mutableStateOf(false) }
                if (showAccessibilityDisclosure) {
                    AccessibilityDisclosureDialog(
                        onKidDevice = false,
                        onAgree = {
                            showAccessibilityDisclosure = false
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onDismiss = { showAccessibilityDisclosure = false }
                    )
                }
                val navController = rememberNavController()
                var signedIn by remember { mutableStateOf(repository.currentUid != null) }

                if (!signedIn) {
                    AuthScreen(repository = repository, onSignedIn = { signedIn = true })
                } else {
                    var passcodeChecked by remember { mutableStateOf(false) }
                    var passcode by remember { mutableStateOf<PasscodeInfo?>(null) }
                    var locked by remember { mutableStateOf(!AppLockState.unlockedThisSession) }

                    LaunchedEffect(signedIn) {
                        // A transient Firestore failure (e.g. briefly offline) shouldn't crash
                        // the app - fail open, the same as "no passcode set yet." This isn't a
                        // real security regression: it only affects this app's own lock screen,
                        // and whoever has the phone unlocked already has physical access to it.
                        passcode = try {
                            repository.getParentPasscode(repository.currentUid!!)
                        } catch (e: Exception) {
                            null
                        }
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
                            repository = repository,
                            onUnlocked = {
                                AppLockState.unlockedThisSession = true
                                locked = false
                            },
                            onPasscodeReset = { info ->
                                passcode = info
                                AppLockState.unlockedThisSession = true
                                locked = false
                            }
                        )
                    } else if (passcodeChecked) {
                        NavHost(navController = navController, startDestination = "dashboard") {
                            composable("dashboard") {
                                DashboardScreen(
                                    repository = repository,
                                    onOpenChild = { childId -> navController.navigate("child/$childId") },
                                    onOpenSettings = { navController.navigate("settings") },
                                    onOpenAppearance = { navController.navigate("appearance") },
                                    // Straight to the detailed screen once tracking is on; the opt-in screen before.
                                    onOpenSelfTracking = {
                                        val selfId = selfProfileStore.childId
                                        if (isSelfTracking && selfId != null) navController.navigate("child/$selfId") else navController.navigate("self")
                                    },
                                    onOpenHelp = { navController.navigate("help") },
                                    onOpenDigest = { navController.navigate("digest") },
                                    onRequestNotificationListener = {
                                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    },
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
                                val isMe = childId == selfProfileStore.childId
                                ChildDetailScreen(
                                    repository = repository,
                                    childId = childId,
                                    onOpenReport = { navController.navigate("report/$childId") },
                                    onBack = { navController.popBackStack() },
                                    onOpenPermissions = if (isMe) ({ navController.navigate("self_permissions") }) else null,
                                    permissionsMissing = isMe && !selfPermissions.allGranted
                                )
                            }
                            composable(
                                "report/{childId}",
                                arguments = listOf(navArgument("childId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val childId = backStackEntry.arguments?.getString("childId")!!
                                WeeklyReportScreen(
                                    repository = repository,
                                    childId = childId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("digest") {
                                val digestStore = remember { NotificationDigestStore(this@MainActivity) }
                                NotificationDigestScreen(
                                    loadEntries = { digestStore.entries },
                                    onDone = { navController.popBackStack() }
                                )
                            }
                            composable("help") {
                                HelpBotScreen(
                                    audience = HelpAudience.PARENT,
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
                                    onStartTracking = {
                                        val self = repository.getOrCreateSelfProfile(repository.currentUid!!, "Me")
                                        selfProfileStore.childId = self.id
                                        isSelfTracking = true
                                        ContextCompat.startForegroundService(
                                            this@MainActivity,
                                            Intent(this@MainActivity, ScreenMonitorService::class.java)
                                        )
                                        (application as ParentApp).startSelfTrackingListener()
                                        // Straight to their own screen time, replacing this opt-in screen.
                                        navController.navigate("child/${self.id}") { popUpTo("self") { inclusive = true } }
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("self_permissions") {
                                SelfPermissionsScreen(
                                    permissions = selfPermissions,
                                    onStopTracking = {
                                        selfProfileStore.clear()
                                        isSelfTracking = false
                                        navController.popBackStack("dashboard", false)
                                    },
                                    onRequestOverlay = {
                                        startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:$packageName")
                                            )
                                        )
                                    },
                                    onRequestAccessibility = { showAccessibilityDisclosure = true },
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
                                    onRequestNotificationListener = {
                                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
