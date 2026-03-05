# FridaHelper — Suggestions

## Implemented
- [x] Smart variable naming (class-derived vars, type-aware params)
- [x] Library name in address mode (base+offset resolution)
- [x] Multi-hook composition with ScriptComposer
- [x] Dark/Light/System theme toggle
- [x] UI/UX polish (accessibility, CoordinatorLayout, imeOptions, helper text)
- [x] waitForLoad dynamic target resolution fix

## Potential Improvements

### High Value

1. ~~**Editable script output**~~ — **Done (v3.6.0)** — Option B: read-only by default, Edit/Done toggle switches to editable EditText, Reset reverts to original. Copy/Export use the current (possibly edited) text.

2. **Script templates / presets** — Save frequently used hook configurations (e.g. "SSL pinning bypass", "Activity lifecycle logger") as reusable templates. Users could load a template and just change the target class.

3. **Smali signature autocomplete from APK** — Integrate with a loaded APK's class list to provide autocomplete suggestions for class names and method signatures, reducing typos.

4. **Live script preview** — Show a real-time preview of the generated script as the user types, instead of requiring a button press. Debounce at ~300ms to avoid excessive generation.

5. **Script history** — Keep a history of recently generated scripts with timestamps. Allow re-loading from history instead of re-entering parameters.

6. **Return value logging / modification** — Add a toggle to log or modify the return value in generated hooks (`retval.replace(...)` for native, `return ...` for Java). Currently only argument logging is generated.

### Medium Value

7. **Batch import from text** — Paste multiple smali signatures (one per line) and auto-queue them all, instead of adding one at a time.

8. **Share intent** — Add a share button alongside copy/export, allowing direct sharing of the script to other apps (Telegram, Notes, etc.) via Android share sheet.

9. **Syntax highlighting in output** — Use a simple span-based highlighter for JavaScript keywords (`var`, `function`, `return`) in the output TextView, improving readability.

10. **Script diff view** — When regenerating a hook for the same target, show what changed (useful for comparing with/without setTimeout or Java.perform wrapping).

11. **Landscape layout optimization** — Create `layout-land/` variants for the fragment layouts that use a side-by-side arrangement (input left, output right) on landscape/tablet.

### Low Priority / Nice-to-Have

12. **Gradle CLI module publishing** — Publish the CLI jar to GitHub Packages or Maven Central so users can install via `brew` or `scoop`.

13. **Custom Frida script templates** — Allow users to define their own script templates with `{{className}}`, `{{methodName}}` placeholders that get substituted during generation.

14. **ADB integration** — One-tap "push to device" that runs `adb push` to copy the script to `/data/local/tmp/` on a connected device.

15. **Widget for quick hook** — An Android home screen widget with a text field for quick smali signature entry and one-tap generate+copy.

16. **Obfuscation mapping support** — Load a ProGuard/R8 `mapping.txt` and auto-translate obfuscated names to their original names in generated hook comments.
