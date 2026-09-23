package org.openscreentime.parent.data

import android.content.Context

/**
 * How this parent app is being used - kept on this phone, because it is a choice about this
 * phone's own screens and nothing else.
 */
class HouseholdPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("household", Context.MODE_PRIVATE)

    /**
     * Someone using the app only for their own phone, with no child's phone to link. The dashboard
     * then stops asking about one. Reversible from the menu at any time, so choosing it is never a
     * way to lose the option.
     */
    var ownPhoneOnly: Boolean
        get() = prefs.getBoolean(KEY_OWN_PHONE_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_OWN_PHONE_ONLY, value).apply()

    /** The family-passcode prompt, put aside for now. The passcode itself is still in the menu. */
    var passcodePromptDismissed: Boolean
        get() = prefs.getBoolean(KEY_PASSCODE_PROMPT_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_PASSCODE_PROMPT_DISMISSED, value).apply()

    private companion object {
        const val KEY_OWN_PHONE_ONLY = "own_phone_only"
        const val KEY_PASSCODE_PROMPT_DISMISSED = "passcode_prompt_dismissed"
    }
}
