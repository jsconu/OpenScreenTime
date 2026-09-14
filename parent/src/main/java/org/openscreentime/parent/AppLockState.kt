package org.openscreentime.parent

/**
 * Whether the passcode gate has already been passed this process lifetime.
 * Deliberately in-memory only, so the app re-locks on every fresh launch
 * (after the process has died) but doesn't re-prompt on every internal
 * navigation while it's running.
 */
object AppLockState {
    var unlockedThisSession: Boolean = false
}
