# FridaHelper — Suggestions

## Implemented (v4.0.0)
- [x] Smart variable naming (class-derived vars, type-aware params, obfuscated naming with last 2 segments)
- [x] Library name in address mode (base+offset resolution)
- [x] Multi-hook composition with ScriptComposer (deduplicated Java.use, waitForLoad by library)
- [x] Dark/Light/System theme toggle
- [x] UI/UX polish (M3 green tonal palette, AnimatedVisibility, collapsible queue)
- [x] waitForLoad dynamic target resolution (both EXPORT and ADDRESS modes)
- [x] Editable script output with syntax highlighting (VisualTransformation)
- [x] Stack trace logging (Java + Native)
- [x] Batch import from .smali files/directories
- [x] Custom export directory + custom filename
- [x] Hex address validation
- [x] Text selection in code display
- [x] SAF folder picker in batch import
- [x] Auto-scroll with persistence
- [x] Kotlin + Jetpack Compose migration

## Potential Improvements

### High Value

1. **Method selection after batch scan** — Currently batch import adds all filtered methods to the queue. A screen showing parsed methods with checkboxes (select/deselect individual methods) would give users much more control, especially for large smali directories with hundreds of methods.

2. **Script templates / presets** — Save frequently used hook configurations (e.g. "SSL pinning bypass", "Activity lifecycle logger") as reusable templates. Users could load a template and just change the target class.

3. **Smali signature autocomplete from APK** — Integrate with a loaded APK's class list to provide autocomplete suggestions for class names and method signatures, reducing typos.

4. **Snippet library / favorites** — Power users regenerate the same hooks repeatedly. A local SQLite or JSON store of saved hooks (with labels) would save significant time. Could include "star" button on generated scripts.

5. **Frida script validation** — A lightweight "dry run" that checks for obvious issues (mismatched braces, undefined variables) before export. Even bracket counting would catch common editing mistakes.

6. **ProGuard mapping support** — Load a `mapping.txt` and auto-resolve obfuscated names back to original names in generated scripts. This would be a significant differentiator from other Frida tools.

### Medium Value

7. **Live script preview** — Show a real-time preview of the generated script as the user types, instead of requiring a button press. Debounce at ~300ms to avoid excessive generation.

8. **Script history** — Keep a history of recently generated scripts with timestamps. Allow re-loading from history instead of re-entering parameters.

9. **Return value logging / modification** — Add a toggle to log or modify the return value in generated hooks (`retval.replace(...)` for native, `return ...` for Java).

10. **Batch import from text** — Paste multiple smali signatures (one per line) and auto-queue them all, instead of adding one at a time.

11. **Share intent** — Add a share button alongside copy/export, allowing direct sharing of the script to other apps (Telegram, Notes, etc.) via Android share sheet.

12. **Script diff view** — When regenerating a hook for the same target, show what changed (useful for comparing with/without setTimeout or Java.perform wrapping).

13. **Dark-only code theme** — Keep the code viewer block always dark regardless of app theme (like VS Code terminals). The green-on-dark looks great in dark mode but could look awkward in light mode.

14. **Compose UI tests** — The core has great test coverage (158 tests), but the Compose screens have none. Even a handful of `ComposeTestRule` tests for critical flows (generate, add to queue, compose) would catch regressions.

### Low Priority / Nice-to-Have

15. **Version-check nudge** — A simple check against the GitHub releases API on app start (once per day) to notify users of updates.

16. **Gradle CLI module publishing** — Publish the CLI jar to GitHub Packages or Maven Central so users can install via `brew` or `scoop`.

17. **Custom Frida script templates** — Allow users to define their own script templates with `{{className}}`, `{{methodName}}` placeholders that get substituted during generation.

18. **ADB integration** — One-tap "push to device" that runs `adb push` to copy the script to `/data/local/tmp/` on a connected device.

19. **Widget for quick hook** — An Android home screen widget with a text field for quick smali signature entry and one-tap generate+copy.

20. **Landscape layout optimization** — Compose adaptive layouts for side-by-side arrangement (input left, output right) on landscape/tablet.
