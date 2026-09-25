package dev.kimi2api.store

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("kimi_settings", Context.MODE_PRIVATE)

    var port: Int
        get() = prefs.getInt("port", 9980)
        set(v) = prefs.edit().putInt("port", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "sk-kimi-local") ?: "sk-kimi-local"
        set(v) = prefs.edit().putString("api_key", v).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean("auto_start", true)
        set(v) = prefs.edit().putBoolean("auto_start", v).apply()

    var showFloat: Boolean
        get() = prefs.getBoolean("show_float", true)
        set(v) = prefs.edit().putBoolean("show_float", v).apply()

    var useSearch: Boolean
        get() = prefs.getBoolean("use_search", false)
        set(v) = prefs.edit().putBoolean("use_search", v).apply()

    var modelName: String
        get() = prefs.getString("model_name", "kimi") ?: "kimi"
        set(v) = prefs.edit().putString("model_name", v).apply()
}
