package org.openscreentime.shared.util

/**
 * Things that exist in this codebase but are not part of a release.
 *
 * A feature listed here is switched off in one place rather than deleted, so that whoever picks it
 * up starts from working code and a real design instead of an empty issue. Nothing offers it, and
 * nothing enforces it, while it is off.
 */
object Features {

    /**
     * "Dumb phone" (Focus mode): a plain home screen keeping calls, texts, sign-in codes and a
     * short list of allowed apps, with a Travel profile for adults.
     *
     * **Off, because it does not work well enough to put in front of a family.** Replacing a
     * phone's home screen touches the launcher role, the foreground guard and the app list all at
     * once, and getting it wrong leaves someone holding a phone that will not open anything. The
     * pieces are all still here - FocusMode, FocusHome, FocusLauncherScreen, FocusDevice and the
     * profile fields they read - and are a good starting point for a contributor with the hardware
     * and the patience to finish it.
     *
     * While this is false: no screen offers dumb phone, the phone never asks to become the home
     * app, the launcher activity stays disabled in the manifest, and a `focusMode` flag arriving
     * from a parent's phone is ignored rather than enforced.
     */
    const val DUMB_PHONE = false
}
