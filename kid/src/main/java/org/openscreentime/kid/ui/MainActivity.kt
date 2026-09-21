package org.openscreentime.kid.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import org.openscreentime.sharedui.CrashReportDialog
import org.openscreentime.shared.util.CrashNote
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.shared.util.PasscodeHasher
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.openscreentime.kid.Backend
import org.openscreentime.kid.nearby.NearbySyncRunner
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.data.AppearancePrefs
import org.openscreentime.kid.data.NotificationDigestStore
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.openscreentime.kid.data.NearbyStore
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.shared.nearby.NearbyLink
import org.openscreentime.kid.monitor.DnsSinkholeVpnService
import org.openscreentime.kid.monitor.LiveChildState
import org.openscreentime.kid.monitor.ScreenMonitorService
import org.openscreentime.kid.util.PermissionActions
import org.openscreentime.kid.util.checkPermissions
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.HelpAudience
import org.openscreentime.shared.model.calmParentStatusLabel
import org.openscreentime.shared.model.computeStreak
import org.openscreentime.shared.model.todayDateString
import org.openscreentime.shared.repo.Profiles
import org.openscreentime.sharedui.ScreenUnavailable
import org.openscreentime.sharedui.AccessibilityDisclosureDialog
import org.openscreentime.sharedui.HelpBotScreen
import org.openscreentime.sharedui.NotificationDigestScreen

private enum class KidScreen { STATUS, PARENT_UNLOCK, PARENT_CONTROLS, PROPOSE_CHANGE, NOTIFICATION_DIGEST, HELP, SETTINGS }

class MainActivity : ComponentActivity() {

    private lateinit var pairingStore: PairingStore

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Set by the Compose layer so a scan result can be handed back to it. */
    private var onScanned: ((String) -> Unit)? = null

    /**
     * Opens the scanner and hands back a link if what was scanned is one of ours. A cancelled scan,
     * or a code that turns out to be something else entirely, simply does nothing.
     */
    private fun startNearbyScan(onLink: (NearbyLink) -> Unit) {
        onScanned = { payload -> NearbyLink.fromPayload(payload)?.let(onLink) }
        scanLauncher.launch(
            ScanOptions()
                .setPrompt("Point at the code on the parent's phone")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    /**
     * Scanning the QR code on a parent's phone. A cancelled scan, or a code that turns out to be
     * something else entirely, simply does nothing - see NearbyLink.fromPayload.
     */
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { onScanned?.invoke(it) }
    }

    // Set as the phone's home screen, for the simple phone (see #42).
    private val homeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) startWebsiteFilterService()
        }

    private fun startWebsiteFilterService() {
        ContextCompat.startForegroundService(this, Intent(this, DnsSinkholeVpnService::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingStore = PairingStore(this)
        val repository = (application as KidApp).repository
        val appearancePrefs = AppearancePrefs(this)

        setContent {
            var themeMode by remember { mutableStateOf(appearancePrefs.themeMode) }
            var textSize by remember { mutableStateOf(appearancePrefs.textSize) }

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
                        onKidDevice = true,
                        onAgree = {
                            showAccessibilityDisclosure = false
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onDismiss = { showAccessibilityDisclosure = false }
                    )
                }
                // A local build has no second phone to pair with: this phone's own profile is
                // already here, so it opens on the status screen (see Backend).
                var paired by remember { mutableStateOf(Backend.IS_LOCAL || pairingStore.isPaired) }
                var permissions by remember { mutableStateOf(checkPermissions(this@MainActivity)) }
                var screen by remember { mutableStateOf(KidScreen.STATUS) }
                // Null until this phone has scanned a parent's code (local builds only).
                var linkedParentName by remember {
                    mutableStateOf(NearbyStore(this@MainActivity).linkStore.link()?.peerName)
                }
                var nearbyLastSyncedAtMs by remember {
                    mutableLongStateOf(NearbyStore(this@MainActivity).linkStore.lastSyncedAtMs)
                }
                var nearbySyncing by remember { mutableStateOf(false) }
                val syncScope = rememberCoroutineScope()

                /**
                 * Reaching the parent's phone, from the button or on its own.
                 *
                 * [attempts] exists for the moment just after scanning: the parent may still be
                 * putting their phone down, and a single try that lands a second too early would
                 * leave them watching a code with nothing happening. Each attempt is cheap and
                 * silent, and the first one that gets through ends it.
                 */
                fun syncNearbyNow(attempts: Int = 1) {
                    if (nearbySyncing) return
                    nearbySyncing = true
                    syncScope.launch {
                        var reached = false
                        repeat(attempts) { attempt ->
                            if (reached) return@repeat
                            if (attempt > 0) delay(3_000)
                            reached = withContext(Dispatchers.IO) {
                                NearbySyncRunner(this@MainActivity).syncNow((application as KidApp).repository)
                            }
                        }
                        nearbyLastSyncedAtMs = NearbyStore(this@MainActivity).linkStore.lastSyncedAtMs
                        nearbySyncing = false
                    }
                }

                // Just linked: reach their phone now rather than in fifteen minutes' time, and keep
                // trying for a few seconds so the parent sees it while still holding the phone.
                LaunchedEffect(linkedParentName) {
                    if (linkedParentName != null) {
                        // Listen under the new key straight away, so the parent's "Sync now" works
                        // without restarting this app.
                        (application as KidApp).startNearbyHost()
                        syncNearbyNow(attempts = 5)
                    }
                }

                // Opening this app is the best signal there is that the phone is awake and probably
                // home, which is exactly when the parent's phone is worth trying.
                LifecycleResumeEffect(linkedParentName) {
                    if (linkedParentName != null) syncNearbyNow()
                    onPauseOrDispose { }
                }
                var child by remember { mutableStateOf<ChildProfile?>(null) }
                var streakDays by remember { mutableIntStateOf(0) }
                var parentSelfProfile by remember { mutableStateOf<ChildProfile?>(null) }
                var parentSelfStats by remember { mutableStateOf(DailyStats(date = todayDateString())) }

                // See #18: null whenever the parent hasn't opted into self-tracking (or
                // this device claimed before they did) - the card just doesn't render then.
                LaunchedEffect(pairingStore.parentUid) {
                    val parentUid = pairingStore.parentUid ?: return@LaunchedEffect
                    parentSelfProfile = repository.getParentSelfProfile(parentUid)
                }

                DisposableEffect(parentSelfProfile?.id) {
                    val parentUid = pairingStore.parentUid
                    if (parentUid == null || parentSelfProfile == null) return@DisposableEffect onDispose {}
                    val reg = repository.listenDailyStats(parentUid, Profiles.SELF_CHILD_ID, todayDateString()) {
                        parentSelfStats = it
                    }
                    onDispose { reg.remove() }
                }

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

                // Starting an already-running foreground service is a harmless no-op (the
                // service itself guards against restarting its tunnel loop), so this just
                // re-asserts "the filter should be running" whenever consent is already in
                // place - covers the service having been killed and needing to come back.
                LaunchedEffect(paired, permissions.vpn) {
                    if (paired && permissions.vpn) startWebsiteFilterService()
                }

                DisposableEffect(paired) {
                    val ids = Backend.profileIds(this@MainActivity)
                    if (!paired || ids == null) {
                        return@DisposableEffect onDispose {}
                    }
                    val (parentUid, childId) = ids
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
                    // System back from any sub-screen returns to the status screen instead of leaving the app.
                    BackHandler(enabled = screen != KidScreen.STATUS) { screen = KidScreen.STATUS }
                    // The monitoring service (and with it the status-bar icon) was only started right
                    // after pairing and at boot, so after an app update or a killed process it stayed
                    // off until the next reboot. Starting it on every launch is harmless if it's
                    // already running.
                    LaunchedEffect(Unit) {
                        ContextCompat.startForegroundService(
                            this@MainActivity,
                            Intent(this@MainActivity, ScreenMonitorService::class.java)
                        )
                    }
                    val permissionActions = PermissionActions(
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
                                onRequestVpn = {
                                    val consentIntent = VpnService.prepare(this@MainActivity)
                                    if (consentIntent != null) {
                                        vpnPermissionLauncher.launch(consentIntent)
                                    } else {
                                        startWebsiteFilterService()
                                    }
                                }
                            )
                    when (screen) {
                        KidScreen.STATUS -> StatusScreen(
                            showNearbyLink = Backend.IS_LOCAL,
                            nearbyLinkedTo = linkedParentName,
                            nearbyLastSyncedAtMs = nearbyLastSyncedAtMs,
                            nearbySyncing = nearbySyncing,
                            onLinkParentPhone = { startNearbyScan { link ->
                                NearbyStore(this@MainActivity).linkStore.save(link)
                                linkedParentName = link.peerName
                            } },
                            onSyncNow = { syncNearbyNow() },
                            childName = pairingStore.childName ?: "",
                            permissions = permissions,
                            streakDays = streakDays,
                            parentStatusLabel = parentSelfProfile?.let { calmParentStatusLabel(it, parentSelfStats) },
                            parentStats = parentSelfProfile?.let { parentSelfStats },
                            showUnlocks = child?.let { it.trackUnlocks && it.showUnlocksOnKid } == true,
                            showNotifications = child?.let { it.trackNotifications && it.showNotificationsOnKid } == true,
                            onOpenSettings = { screen = KidScreen.SETTINGS },
                            onOpenParentMode = { screen = KidScreen.PARENT_UNLOCK },
                            onOpenHelp = { screen = KidScreen.HELP },
                            onProposeChange = { screen = KidScreen.PROPOSE_CHANGE },
                            onOpenNotificationDigest = { screen = KidScreen.NOTIFICATION_DIGEST },
                            onRequestNotificationListener = {
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        )
                        KidScreen.SETTINGS -> KidSettingsScreen(
                            permissions = permissions,
                            themeMode = themeMode,
                            textSize = textSize,
                            permissionActions = permissionActions,
                            onCycleTheme = { themeMode = appearancePrefs.cycleThemeMode() },
                            onCycleTextSize = { textSize = appearancePrefs.cycleTextSize() },
                            onOpenColorSettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onRequestNotificationListener = {
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            onRequestHomeScreen = { homeRoleLauncher.launch(FocusLauncherActivity.focusMode(this@MainActivity).homeRoleIntent()) },
                            linkedParentName = linkedParentName,
                            showNearbyLink = Backend.IS_LOCAL,
                            onLinkParentPhone = { startNearbyScan { link ->
                                NearbyStore(this@MainActivity).linkStore.save(link)
                                linkedParentName = link.peerName
                            } },
                            onUnlinkParentPhone = {
                                NearbyStore(this@MainActivity).linkStore.forget()
                                linkedParentName = null
                            },
                            showUnpair = child?.parentPasscodeHash == null,
                            onUnpair = {
                                pairingStore.clear()
                                LiveChildState.clear(this@MainActivity)
                                paired = false
                            },
                            onBack = { screen = KidScreen.STATUS }
                        )
                        KidScreen.HELP -> HelpBotScreen(
                            audience = HelpAudience.KID,
                            onBack = { screen = KidScreen.STATUS }
                        )
                        KidScreen.PARENT_UNLOCK -> ParentModeUnlockScreen(
                            child = child,
                            onUnlocked = { screen = KidScreen.PARENT_CONTROLS },
                            onCancel = { screen = KidScreen.STATUS },
                            // Only a local build sets its own: in a cloud build the parent's account
                            // owns the passcode and copies it down, and two sources would disagree.
                            onSetPasscodeHere = if (Backend.IS_LOCAL) {
                                { entered ->
                                    val repository = (application as KidApp).repository
                                    // PBKDF2 is slow on purpose - off the main thread, as its docs say.
                                    val (hash, salt) = withContext(Dispatchers.Default) {
                                        val salt = PasscodeHasher.randomSalt()
                                        PasscodeHasher.hash(entered, salt) to salt
                                    }
                                    repository.setParentPasscode(
                                        repository.currentUid.orEmpty(),
                                        hash,
                                        salt
                                    )
                                }
                            } else {
                                null
                            }
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
                                    onDone = { screen = KidScreen.STATUS },
                                    onUnpair = {
                                        pairingStore.clear()
                                        LiveChildState.clear(this@MainActivity)
                                        paired = false
                                        screen = KidScreen.STATUS
                                    }
                                )
                            } else {
                                ScreenUnavailable(
                                    message = "Still loading, or this phone isn't linked to a family any more.",
                                    onBack = { screen = KidScreen.STATUS },
                                    actionLabel = "Unpair this device",
                                    onAction = {
                                        pairingStore.clear()
                                        LiveChildState.clear(this@MainActivity)
                                        paired = false
                                        screen = KidScreen.STATUS
                                    }
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
                            } else {
                                ScreenUnavailable(
                                    message = "Still loading, or this phone isn't linked to a family any more.",
                                    onBack = { screen = KidScreen.STATUS },
                                    actionLabel = "Unpair this device",
                                    onAction = {
                                        pairingStore.clear()
                                        LiveChildState.clear(this@MainActivity)
                                        paired = false
                                        screen = KidScreen.STATUS
                                    }
                                )
                            }
                        }
                        KidScreen.NOTIFICATION_DIGEST -> {
                            val digestStore = remember { NotificationDigestStore(this@MainActivity) }
                            NotificationDigestScreen(
                                loadEntries = { digestStore.entries },
                                onDone = { screen = KidScreen.STATUS }
                            )
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
