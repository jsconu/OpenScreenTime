package org.openscreentime.shared.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.view.inputmethod.InputMethodManager
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.FocusConfig
import org.openscreentime.shared.model.FocusProfile

/**
 * The phone's own copy of a person's Focus mode ("dumb phone", see #42): what a parent chose, plus a local-only
 * "all apps" window. Kept on the phone so enforcement never waits on the network, and refreshed whenever the
 * profile changes. One instance per app (the kid app for a child's phone, the parent app for the parent's own).
 */
class FocusPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)

    fun config(): FocusConfig = FocusConfig(
        enabled = prefs.getBoolean("enabled", false),
        profile = FocusProfile.fromWireValue(prefs.getString("profile", null)),
        allowed = prefs.getStringSet("allowed", emptySet()).orEmpty(),
        travelAllowed = prefs.getStringSet("travelAllowed", emptySet()).orEmpty()
    )

    /** Until when everything is open (an adult chose "All apps", or a parent unlocked a child's phone). */
    var openUntilMs: Long?
        get() = prefs.getLong("openUntilMs", -1L).takeIf { it > 0 }
        set(value) = prefs.edit().putLong("openUntilMs", value ?: -1L).apply()

    /** Copies a freshly-received profile onto the phone, and turns the launcher option on or off to match. */
    fun update(context: Context, child: ChildProfile, launcher: ComponentName) {
        prefs.edit()
            .putBoolean("enabled", child.focusMode)
            .putString("profile", child.focusProfile)
            .putStringSet("allowed", child.focusAllowedPackages.toSet())
            .putStringSet("travelAllowed", child.travelAllowedPackages.toSet())
            .apply()
        if (!child.focusMode) openUntilMs = null
        setLauncherEnabled(context, launcher, child.focusMode)
    }

    /** Local-only switch, used to apply a change instantly (the profile update follows through Firestore). */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
    }

    fun clear(context: Context, launcher: ComponentName) {
        prefs.edit().clear().apply()
        setLauncherEnabled(context, launcher, false)
    }
}

/** The launcher activity is switched off until Focus mode is on, so it isn't offered as a home app otherwise. */
fun setLauncherEnabled(context: Context, launcher: ComponentName, enabled: Boolean) {
    val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    if (context.packageManager.getComponentEnabledSetting(launcher) == state) return
    context.packageManager.setComponentEnabledSetting(launcher, state, PackageManager.DONT_KILL_APP)
}

/** True if this app is currently the phone's home screen. */
fun isDefaultHome(context: Context): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName == context.packageName
}

/**
 * The packages this phone needs for basic use, worked out on the device because they differ by phone maker:
 * its dialer, its texting app, its contacts app, every home screen, the keyboard, and this app. The phone and
 * texting apps come first (in that order) so they lead the Focus home screen.
 */
fun resolveEssentialPackages(context: Context): List<String> {
    val pm = context.packageManager
    val ordered = LinkedHashSet<String>()
    fun add(pkg: String?) { if (!pkg.isNullOrEmpty()) ordered += pkg }
    fun resolve(intent: Intent) = try {
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    } catch (e: Exception) {
        null
    }
    add(resolve(Intent(Intent.ACTION_DIAL)))
    add(try { Telephony.Sms.getDefaultSmsPackage(context) } catch (e: Exception) { null })
    add(resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))))
    add(resolve(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)))
    add(context.packageName)
    try {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .forEach { add(it.activityInfo.packageName) }
    } catch (e: Exception) {
        // Ignored: the accessibility check still allows the current home screen through.
    }
    try {
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
            ?.forEach { add(it.packageName) }
    } catch (e: Exception) {
        // Ignored: keyboards also show up as system windows most of the time.
    }
    return ordered.toList()
}
