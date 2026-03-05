package com.amrts.fridahelper.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Manages dark/light theme preference and persistence.
 *
 * Supports three modes:
 * - MODE_SYSTEM: follow system setting (default)
 * - MODE_LIGHT: force light theme
 * - MODE_DARK: force dark theme
 *
 * Preference is stored in SharedPreferences and restored on app launch.
 * Call {@link #applyTheme()} in Application.onCreate() or Activity.onCreate()
 * before setContentView() to ensure the correct mode is active.
 */
public final class ThemeManager {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    private static final String PREFS_NAME = "fridahelper_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    private ThemeManager() { }

    /**
     * Applies the saved theme preference.
     * Call this early in the Activity lifecycle (before setContentView).
     */
    public static void applyTheme(Context context) {
        int mode = getSavedMode(context);
        applyNightMode(mode);
    }

    /**
     * Gets the currently saved theme mode.
     */
    public static int getSavedMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM);
    }

    /**
     * Saves and applies a new theme mode.
     * The Activity will be recreated by AppCompatDelegate automatically.
     */
    public static void setMode(Context context, int mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        applyNightMode(mode);
    }

    /**
     * Cycles through modes: System -> Light -> Dark -> System.
     * Returns the new mode after cycling.
     */
    public static int cycleMode(Context context) {
        int current = getSavedMode(context);
        int next;
        switch (current) {
            case MODE_SYSTEM: next = MODE_LIGHT; break;
            case MODE_LIGHT:  next = MODE_DARK;  break;
            case MODE_DARK:   next = MODE_SYSTEM; break;
            default:          next = MODE_SYSTEM; break;
        }
        setMode(context, next);
        return next;
    }

    /**
     * Maps our mode constants to AppCompatDelegate night mode values.
     */
    private static void applyNightMode(int mode) {
        int nightMode;
        switch (mode) {
            case MODE_LIGHT:
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case MODE_DARK:
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case MODE_SYSTEM:
            default:
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    /**
     * Returns a localized label for the given mode.
     */
    public static String getModeLabel(Context context, int mode) {
        switch (mode) {
            case MODE_LIGHT:  return context.getString(R.string.theme_light);
            case MODE_DARK:   return context.getString(R.string.theme_dark);
            case MODE_SYSTEM:
            default:          return context.getString(R.string.theme_system);
        }
    }
}
