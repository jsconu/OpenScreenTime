package org.openscreentime.shared.util

import android.content.SharedPreferences

/** What [DayLedger] needs from wherever it keeps its numbers: small typed values under string keys, saved in batches. */
interface KeyValueStore {
    fun getString(key: String, default: String?): String?
    fun getLong(key: String, default: Long): Long
    fun getInt(key: String, default: Int): Int
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getStringSet(key: String): Set<String>

    /** Applies every change made inside [block] together. */
    fun edit(block: KeyValueEditor.() -> Unit)
}

interface KeyValueEditor {
    fun putString(key: String, value: String)
    fun putLong(key: String, value: Long)
    fun putInt(key: String, value: Int)
    fun putBoolean(key: String, value: Boolean)
    fun putStringSet(key: String, value: Set<String>)
    fun remove(key: String)
}

/** The phone's copy: Android SharedPreferences. */
class SharedPrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun getStringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet()) ?: emptySet()

    override fun edit(block: KeyValueEditor.() -> Unit) {
        val editor = prefs.edit()
        object : KeyValueEditor {
            override fun putString(key: String, value: String) { editor.putString(key, value) }
            override fun putLong(key: String, value: Long) { editor.putLong(key, value) }
            override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
            override fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
            override fun putStringSet(key: String, value: Set<String>) { editor.putStringSet(key, value) }
            override fun remove(key: String) { editor.remove(key) }
        }.block()
        editor.apply()
    }
}

/** A plain in-memory copy, for tests. */
class InMemoryKeyValueStore : KeyValueStore {
    private val values = HashMap<String, Any>()

    override fun getString(key: String, default: String?): String? = values[key] as? String ?: default
    override fun getLong(key: String, default: Long): Long = values[key] as? Long ?: default
    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String> = values[key] as? Set<String> ?: emptySet()

    override fun edit(block: KeyValueEditor.() -> Unit) {
        object : KeyValueEditor {
            override fun putString(key: String, value: String) { values[key] = value }
            override fun putLong(key: String, value: Long) { values[key] = value }
            override fun putInt(key: String, value: Int) { values[key] = value }
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
            override fun putStringSet(key: String, value: Set<String>) { values[key] = value.toSet() }
            override fun remove(key: String) { values.remove(key) }
        }.block()
    }
}
