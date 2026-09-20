package org.openscreentime.shared.util

import android.accessibilityservice.AccessibilityService
import android.widget.Toast
import org.openscreentime.shared.model.isFocusAllowed

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
