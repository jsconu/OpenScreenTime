package org.openscreentime.shared.util

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.view.inputmethod.InputMethodManager
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.FocusApp
import org.openscreentime.shared.model.FocusConfig
import org.openscreentime.shared.model.FocusProfile
import org.openscreentime.shared.model.focusHomePackages

/**
 * A phone's "dumb phone" Focus mode (see #42), everything an app needs to know about it in one place.
 *
 * Each app builds one with its own launcher activity (the plain home screen, kept switched off until Focus is on)
 * and then only asks questions or reports events:
 *  - [sync] when a fresh profile arrives, [stop] when the phone stops being tracked or is unpaired,
 *    [turnOffHere] when the person turns it off on this phone (applied at once, the profile follows);
 *  - [config], [openUntilMs], [homeApps], [allApps] for the home screen, and [enabled] for settings rows;
 *  - [isDefaultHome] / [homeRoleIntent] for the "make this the home app" step.
 * Enforcement itself is [FocusEnforcer], driven by the accessibility service.
 *
 * The choice is copied onto the phone so nothing waits on the network. One instance per call is fine; it holds no
 * state beyond the saved copy.
 */
class FocusMode(private val context: Context, private val launcher: ComponentName) {
    private val prefs = FocusPrefs(context)

    val config: FocusConfig get() = prefs.config()

    val enabled: Boolean get() = config.enabled

    /** Until when everything is open (an adult chose "All apps", or a parent unlocked a child's phone). */
    var openUntilMs: Long?
        get() = prefs.openUntilMs
        set(value) { prefs.openUntilMs = value }

    /** Copies a freshly-received profile onto the phone, and offers the home screen only while Focus is on. */
    fun sync(child: ChildProfile) {
        prefs.update(child)
        setLauncherEnabled(child.focusMode)
    }

    /** Switches the profile (Everyday or Travel) on this phone straight away; the saved profile follows. */
    fun setProfileHere(profile: FocusProfile) {
        prefs.setProfile(profile)
    }

    /** This phone is no longer tracked (or unpaired): forget everything and stop offering the home screen. */
    fun stop() {
        prefs.clear()
        setLauncherEnabled(false)
    }

    /** Turns Focus off on this phone straight away, without waiting for the profile change to round-trip. */
    fun turnOffHere() {
        prefs.setEnabled(false)
        prefs.openUntilMs = null
        setLauncherEnabled(false)
    }

    /** What the home screen lists: the core apps first, then the allowed ones, A to Z. */
    fun homeApps(config: FocusConfig = this.config): List<FocusApp> {
        val labels = launchableLabels(context)
        return focusHomePackages(labels.keys, resolveCoreAppPackages(context), config)
            .mapNotNull { pkg -> labels[pkg]?.let { FocusApp(pkg, it) } }
    }

    /** Every launchable app, A to Z - shown only while an "all apps" window is open. */
    fun allApps(): List<FocusApp> =
        launchableLabels(context).map { FocusApp(it.key, it.value) }.sortedBy { it.label.lowercase() }

    /** True if this app is currently the phone's home screen. */
    fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** The system prompt (or Settings, on older phones) that makes this app the phone's home screen. */
    fun homeRoleIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }

    private fun setLauncherEnabled(enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (context.packageManager.getComponentEnabledSetting(launcher) == state) return
        context.packageManager.setComponentEnabledSetting(launcher, state, PackageManager.DONT_KILL_APP)
    }
}

/** The saved copy of a Focus choice, plus the local-only "all apps" window. */
internal class FocusPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)

    fun config(): FocusConfig = FocusConfig(
        enabled = prefs.getBoolean("enabled", false),
        profile = FocusProfile.fromWireValue(prefs.getString("profile", null)),
        allowed = prefs.getStringSet("allowed", emptySet()).orEmpty(),
        travelAllowed = prefs.getStringSet("travelAllowed", emptySet()).orEmpty()
    )

    var openUntilMs: Long?
        get() = prefs.getLong("openUntilMs", -1L).takeIf { it > 0 }
        set(value) = prefs.edit().putLong("openUntilMs", value ?: -1L).apply()

    fun update(child: ChildProfile) {
        prefs.edit()
            .putBoolean("enabled", child.focusMode)
            .putString("profile", child.focusProfile)
            .putStringSet("allowed", child.focusAllowedPackages.toSet())
            .putStringSet("travelAllowed", child.travelAllowedPackages.toSet())
            .apply()
        if (!child.focusMode) openUntilMs = null
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
    }

    fun setProfile(profile: FocusProfile) {
        prefs.edit().putString("profile", profile.wireValue).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

/**
 * The apps the Focus home screen always leads with, in order: the phone's dialer, its texting app, its contacts,
 * and this app. Worked out on the device because they differ by phone maker.
 */
internal fun resolveCoreAppPackages(context: Context): List<String> {
    val pm = context.packageManager
    val ordered = LinkedHashSet<String>()
    fun resolve(intent: Intent): String? = try {
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
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

/**
 * Every package this phone needs for basic use: the core apps (in home-screen order), plus every home screen and the
 * keyboard, which the home screen doesn't list but enforcement must never send away.
 */
fun resolveEssentialPackages(context: Context): List<String> {
    val pm = context.packageManager
    val ordered = LinkedHashSet(resolveCoreAppPackages(context))
    try {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .forEach { ordered += it.activityInfo.packageName }
    } catch (e: Exception) {
        // Ignored: the accessibility check still allows the current home screen through.
    }
    try {
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
            ?.forEach { ordered += it.packageName }
    } catch (e: Exception) {
        // Ignored: keyboards also show up as system windows most of the time.
    }
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
