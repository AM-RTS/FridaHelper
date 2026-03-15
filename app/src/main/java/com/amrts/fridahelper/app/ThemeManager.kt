package com.amrts.fridahelper.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Manages dark/light theme preference and persistence.
 *
 * Supports three modes: MODE_SYSTEM (default), MODE_LIGHT, MODE_DARK.
 * Preference is stored in SharedPreferences and restored on app launch.
 */
object ThemeManager {

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private const val PREFS_NAME = "fridahelper_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    fun applyTheme(context: Context) {
        applyNightMode(getSavedMode(context))
    }

    fun getSavedMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME_MODE, mode).apply()
        applyNightMode(mode)
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

    private fun applyNightMode(mode: Int) {
        val nightMode = when (mode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun getModeLabel(context: Context, mode: Int): String = when (mode) {
        MODE_LIGHT -> context.getString(R.string.theme_light)
        MODE_DARK -> context.getString(R.string.theme_dark)
        else -> context.getString(R.string.theme_system)
    }
}
