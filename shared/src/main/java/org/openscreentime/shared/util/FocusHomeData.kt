package org.openscreentime.shared.util

import android.accessibilityservice.AccessibilityService
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import org.openscreentime.shared.model.FocusConfig
import org.openscreentime.shared.model.focusHomePackages
import org.openscreentime.shared.model.isFocusAllowed
import org.openscreentime.shared.model.FocusApp

/** The apps the Focus home screen always leads with: the phone's dialer, its texting app, its contacts, this app. */
fun resolveCoreAppPackages(context: Context): List<String> {
    val pm = context.packageManager
    val ordered = LinkedHashSet<String>()
    fun resolve(intent: Intent): String? = try {
        pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    } catch (e: Exception) {
        null
    }
    resolve(Intent(Intent.ACTION_DIAL))?.let(ordered::add)
    (try { Telephony.Sms.getDefaultSmsPackage(context) } catch (e: Exception) { null })?.let(ordered::add)
    resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))?.let(ordered::add)
    resolve(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))?.let(ordered::add)
    ordered += context.packageName
    return ordered.toList()
}

private fun launchableLabels(context: Context): Map<String, String> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return try {
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .toMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

/** What the Focus home screen lists: the core apps first, then the allowed ones, A to Z. */
fun buildFocusHomeApps(context: Context, config: FocusConfig): List<FocusApp> {
    val labels = launchableLabels(context)
    return focusHomePackages(labels.keys, resolveCoreAppPackages(context), config)
        .mapNotNull { pkg -> labels[pkg]?.let { FocusApp(pkg, it) } }
}

/** Every launchable app, A to Z - shown only while an "all apps" window is open. */
fun buildAllApps(context: Context): List<FocusApp> =
    launchableLabels(context).map { FocusApp(it.key, it.value) }.sortedBy { it.label.lowercase() }

/** Opens the system prompt (or Settings, on older phones) that makes this app the phone's home screen. */
fun homeRoleIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME)
    } else {
        Intent(Settings.ACTION_HOME_SETTINGS)
    }

/**
 * "Dumb phone" enforcement (see #42), shared by both apps' accessibility services: when an app that isn't allowed
 * comes to the front, send the phone back to the home screen with a short note. Deliberately gentle - no block
 * screen to dismiss, just home - and rate-limited so it can never loop.
 */
class FocusEnforcer(private val service: AccessibilityService) {
    private var essential: Set<String> = emptySet()
    private var essentialAtMs = 0L
    private var lastActionMs = 0L

    /** True if [packageName] was sent home, in which case the caller should stop handling the event. */
    fun enforce(packageName: String): Boolean {
        val prefs = FocusPrefs(service)
        val config = prefs.config()
        if (!config.enabled) return false
        val now = System.currentTimeMillis()
        if (now - essentialAtMs > 60_000L || essential.isEmpty()) {
            essential = resolveEssentialPackages(service).toSet()
            essentialAtMs = now
        }
        if (isFocusAllowed(packageName, config, essential, prefs.openUntilMs, now)) return false
        if (now - lastActionMs < 1_500L) return true
        lastActionMs = now
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Toast.makeText(service, "Dumb phone is on - that app isn't available right now.", Toast.LENGTH_SHORT).show()
        return true
    }
}

/** Applies a Focus change made on this phone straight away, without waiting for the profile to round-trip. */
fun FocusPrefs.setEnabledLocally(context: Context, launcher: ComponentName, enabled: Boolean) {
    setEnabled(enabled)
    if (!enabled) openUntilMs = null
    setLauncherEnabled(context, launcher, enabled)
}
