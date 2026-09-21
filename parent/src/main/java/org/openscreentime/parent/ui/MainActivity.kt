package org.openscreentime.parent.ui

import android.content.Intent
import android.net.VpnService
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
import androidx.navigation.NavController
import kotlinx.coroutines.withTimeoutOrNull
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.openscreentime.sharedui.CrashReportDialog
import org.openscreentime.shared.util.CrashNote
import org.openscreentime.parent.AppLockState
import org.openscreentime.parent.Backend
import org.openscreentime.parent.data.NearbyStore
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.data.NotificationDigestStore
import org.openscreentime.parent.data.AppearancePrefs
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.parent.monitor.ScreenMonitorService
import org.openscreentime.parent.monitor.SelfDeviceState
import org.openscreentime.parent.monitor.DnsSinkholeVpnService
import org.openscreentime.parent.util.checkPermissions
import org.openscreentime.shared.model.HelpAudience
import org.openscreentime.shared.model.PasscodeInfo
import org.openscreentime.sharedui.ScreenUnavailable
import org.openscreentime.sharedui.AccessibilityDisclosureDialog
import org.openscreentime.sharedui.HelpBotScreen
import org.openscreentime.sharedui.NotificationDigestScreen

class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // The optional website filter is a local VPN, which Android makes you approve in a system prompt.
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) startWebsiteFilter()
        }

    private fun startWebsiteFilter() {
        ContextCompat.startForegroundService(this, Intent(this, DnsSinkholeVpnService::class.java))
    }

    // Set as the phone's home screen, for "Dumb phone" (see #42).
    private val homeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private fun requestHomeScreen() {
        homeRoleLauncher.launch(FocusLauncherActivity.focusMode(this).homeRoleIntent())
    }

    /** Set by the calm-notification summary and tile, which open the calm list straight away. */
    private val openDigest = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_DIGEST, false)) openDigest.value = true
    }

    private fun requestWebsiteFilter() {
        val consent = VpnService.prepare(this)
        if (consent != null) vpnPermissionLauncher.launch(consent) else startWebsiteFilter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as ParentApp).repository
        val appearancePrefs = AppearancePrefs(this)
        val selfProfileStore = SelfProfileStore(this)
        if (intent.getBooleanExtra(EXTRA_OPEN_DIGEST, false)) openDigest.value = true

        // If the parent already allowed the website filter, keep it running while they track themselves.
        if (selfProfileStore.isTracking && VpnService.prepare(this) == null) startWebsiteFilter()

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
              // After a crash, offer the details to copy (see CrashNote).
              var crashNote by remember { mutableStateOf(CrashNote.pending(this@MainActivity)) }
              crashNote?.let { note ->
                  CrashReportDialog(details = note, onDismiss = { CrashNote.clear(this@MainActivity); crashNote = null })
              }
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
                // A local build keeps everything on this phone and has no account, so it starts
                // straight on the dashboard - there is nothing to sign in to (see Backend).
                var signedIn by remember { mutableStateOf(Backend.IS_LOCAL || repository.currentUid != null) }

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
                        // Bounded: offline, the read can hang, which used to leave a blank white screen at start.
                        val uid = repository.currentUid
                        if (uid == null) signedIn = false
                        passcode = try {
                            if (uid == null) null else withTimeoutOrNull(PASSCODE_CHECK_TIMEOUT_MS) { repository.getParentPasscode(uid) }
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
                    } else if (!passcodeChecked) {
                        // Checking whether an app passcode is set: show something rather than a blank screen.
                        ScreenUnavailable(message = "Opening OpenScreenTime...")
                    } else {
                        // Opened by the calm summary notification or the Quick Settings tile (after any unlock above).
                        LaunchedEffect(openDigest.value) {
                            if (openDigest.value) {
                                openDigest.value = false
                                navController.navigate("digest")
                            }
                        }
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
                                    onOpenNearbyLink = { navController.navigate("nearby") },
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
                                    onBack = { navController.popBack() },
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
                                    onBack = { navController.popBack() }
                                )
                            }
                            composable("digest") {
                                val digestStore = remember { NotificationDigestStore(this@MainActivity) }
                                NotificationDigestScreen(
                                    loadEntries = { digestStore.entries },
                                    onDone = { navController.popBack() }
                                )
                            }
                            composable("help") {
                                HelpBotScreen(
                                    audience = HelpAudience.PARENT,
                                    onBack = { navController.popBack() }
                                )
                            }
                            composable("nearby") {
                                val nearbyStore = remember { NearbyStore(this@MainActivity).linkStore }
                                var linkedName by remember { mutableStateOf(nearbyStore.link()?.peerName) }
                                NearbyLinkScreen(
                                    linkedName = linkedName,
                                    lastSyncedAtMs = nearbyStore.lastSyncedAtMs,
                                    onLinked = { link ->
                                        nearbyStore.save(link)
                                        linkedName = link.peerName
                                        // Start listening straight away, rather than at the next app start.
                                        (application as ParentApp).startNearbyHost()
                                    },
                                    onUnlink = {
                                        nearbyStore.forget()
                                        linkedName = null
                                    },
                                    onBack = { navController.popBack() }
                                )
                            }
                            composable("settings") {
                                PasscodeSettingsScreen(
                                    repository = repository,
                                    onBack = { navController.popBack() }
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
                                    onBack = { navController.popBack() }
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
                                    onBack = { navController.popBack() }
                                )
                            }
                            composable("self_permissions") {
                                SelfPermissionsScreen(
                                    permissions = selfPermissions,
                                    onStopTracking = {
                                        SelfDeviceState.clear(this@MainActivity)
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
                                    onRequestWebsiteFilter = { requestWebsiteFilter() },
                                    onRequestHomeScreen = { requestHomeScreen() },
                                    onBack = { navController.popBack() }
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
        const val EXTRA_OPEN_DIGEST = "open_digest"
        private const val PASSCODE_CHECK_TIMEOUT_MS = 8_000L
    }
}

/**
 * Goes back one screen, but never pops the last one and ignores a second tap while the first is still animating.
 * A plain popBackStack() on a double tap (or a tap plus the back gesture) removes the dashboard too and leaves a blank
 * white screen.
 */
private fun NavController.popBack() {
    if (previousBackStackEntry != null && currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
