package org.openscreentime.shared.util

import android.content.Context
import android.util.Log

/**
 * Keeps a note of the last crash on the phone, so a tester can copy it and send it. Nothing is uploaded from here: the
 * note is the error's stack trace (which code failed), the app version and the time, saved in this app's private storage
 * until the person sees it on the next launch and copies or dismisses it. It chains to whatever handler was there
 * before, so crash reporting (Crashlytics) still gets the crash.
 */
object CrashNote {
    private const val PREFS = "last_crash_note"
    private const val KEY_NOTE = "note"
    private const val MAX_CHARS = 6_000

    /** Call once from Application.onCreate. */
    fun install(context: Context, appName: String, versionName: String) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_NOTE, describe(appName, versionName, thread.name, Log.getStackTraceString(error)))
                    .commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved note from a crash on an earlier run, if there is one. */
    fun pending(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NOTE, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_NOTE).apply()
    }

    /** The text of a note, kept to a size that is easy to paste. */
    internal fun describe(appName: String, versionName: String, threadName: String, trace: String): String {
        val header = "$appName $versionName crashed on thread $threadName"
        val body = trace.trim()
        val text = header + "\n" + body
        return if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS) + "\n(cut)"
    }
}
