package com.amrts.fridahelper.app

import android.content.Context

/**
 * Manages dark/light theme preference and persistence.
 * Compose reads the mode directly; no AppCompatDelegate needed.
 */
object ThemeManager {

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private const val PREFS_NAME = "fridahelper_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_AUTO_SCROLL = "auto_scroll"
    private const val KEY_EXPORT_DIR = "export_dir"

    fun getSavedMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun cycleMode(context: Context): Int {
        val current = getSavedMode(context)
        val next = when (current) {
            MODE_SYSTEM -> MODE_LIGHT
            MODE_LIGHT -> MODE_DARK
            MODE_DARK -> MODE_SYSTEM
            else -> MODE_SYSTEM
        }
        setMode(context, next)
        return next
    }

    fun getModeLabel(mode: Int): String = when (mode) {
        MODE_LIGHT -> "Light"
        MODE_DARK -> "Dark"
        else -> "System"
    }

    fun getAutoScroll(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SCROLL, true)
    }

    fun setAutoScroll(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SCROLL, enabled).apply()
    }

    fun getExportDir(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EXPORT_DIR, "") ?: ""
    }

    fun setExportDir(context: Context, path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_EXPORT_DIR, path.trim()).apply()
    }
}
