package com.amrts.fridahelper.core;

/**
 * Central version constant for the FridaHelper core module.
 *
 * Usage:
 * - CLI: display in banner and --version flag
 * - Android GUI: show in About screen, debug logs
 * - Compatibility checks: verify core version matches app expectations
 *
 * Update this constant for every release. Keep in sync with build.gradle version.
 */
public final class FridaHelperVersion {

    /** Semantic version string (major.minor.patch). */
    public static final String VERSION = "3.3.1";

    /** Human-readable name shown in UI/CLI banners. */
    public static final String NAME = "FridaHelper";

    /** Combined display string. */
    public static final String FULL = NAME + " " + VERSION;

    /** GitHub repository URL. */
    public static final String REPO_URL = "https://github.com/AM-RTS/FridaHelper";

    private FridaHelperVersion() { }
}
