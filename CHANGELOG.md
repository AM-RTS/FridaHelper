# Changelog

## [4.0.0] - 2026-03-15

### Major: Kotlin + Jetpack Compose Migration
- **App module fully migrated to Kotlin** — all Fragments, Adapters, Helpers → Kotlin files
- **Jetpack Compose Material 3** — replaced XML layouts with Compose UI (`ComponentActivity`, `Scaffold`, `TopAppBar`, `HorizontalPager`, `SecondaryTabRow`)
- **MVVM refactored** — `LiveData` → `StateFlow`/`SharedFlow`, lifecycle-aware collection via `collectAsStateWithLifecycle()`
- **Green tonal palette** — explicit M3 color roles (primaryContainer, secondaryContainer, tertiaryContainer, outline) derived from brand green; no more disconnected purple
- **AnimatedVisibility** — tuned enter/exit animations with M3 easing for script output and hook queue sections
- **Edge-to-edge** — `enableEdgeToEdge()` with proper inset handling

### New Features
- **Stack Trace Logging** — optional per-hook stack trace: `android.util.Log.getStackTraceString` (Java) / `Thread.backtrace` (Native). Helper functions defined once per script
- **Hex Address Validation** — native hook address field rejects non-hex input (e.g. "0xsodidk")
- **Wait for Library in ADDRESS mode** — `waitForLoad` now works with address-based hooks using `Module.findBaseAddress(lib).add(ptr(addr))`
- **Export Directory setting** — settings menu option for custom export path; empty falls back to default
- **Auto-scroll to output** — scrolls to generated script with smooth animation; persisted toggle in settings
- **Custom export filename** — dialog prompt on export with default fallback to timestamped name
- **Collapsible Hook Queue** — auto-collapses when > 3 items; expand/collapse chevron
- **SAF Folder Picker** — folder icon in Batch Import dialog for directory selection
- **Clear Script Output** — button to clear displayed script
- **Text Selection** — `SelectionContainer` wraps code display for partial selection
- **CLI Stack Trace** — CLI now prompts for stack trace enablement on Java, Native, and composed hooks

### Bug Fixes
- **waitForLoad + nativeLog duplication** — composed scripts with multiple native hooks no longer duplicate helper functions; same-library hooks grouped under single `onLibLoaded` callback
- **Generate after Compose** — generating a single hook now properly clears composed output and vice versa
- **Auto-scroll persistence** — setting now saved across app restarts
- **Tab switch scroll glitch** — switching tabs no longer causes unwanted scroll to script output
- **Button flicker** — removed `isGenerating` guard from non-async buttons
- **Error message consumption** — SharedFlow ensures every error triggers a snackbar, even identical consecutive errors
- **Race condition on composer** — batch import now parses on IO, adds to composer on main thread
- **Commented stack trace line** — removed `//console.log(...)` when stack trace is disabled
- **NativeHookGenerator onLeave parameter** — corrected `args` → `retval` in `onLeave` callback

### Code Quality
- **Smart variable naming for obfuscated classes** — uses last 2 package segments (e.g. `example_a`) instead of generic `cls`; collision-avoidance via numeric suffixes
- **`ScriptIndent` utility** — centralized `indentBlock()` from 3 duplicate implementations
- **`ParamNameGenerator.generateArray()`** — eliminates `generate().split()` round-trip
- **Removed dead code** — `ComposeOptionsDialog.kt`, unused `isGenerating` public accessor, no-op `normalizeType()`, unused `ButtonDefaults` import
- **`SmaliMethod` simplified defensive copy** — `Collections.unmodifiableList(new ArrayList<>())`
- **CLI Scanner in try-with-resources** — proper resource management
- **`BatchProcessor` documented as CLI-only** — explicit `java.nio.file.Path` API dependency documented

### Testing
- 158 tests covering parsers, generators, composer, batch processing, and new features

## [3.1.0] - 2026-02-17

### Native Hook Enhancements
- **Address-based hooks**: Hook raw pointers via `ptr("0xABCD")` — no export name needed
- **Null/wildcard library**: Leave library name empty to resolve exports across all loaded modules (`Module.findExportByName(null, "func")`)
- **Wait for library loading**: `android_dlopen_ext` interception pattern for libraries not yet loaded at injection time
- **setTimeout wrapping**: Delayed execution for timing-sensitive hooks
- **Builder pattern for NativeSymbol**: Clean API for combining options without boolean explosion
- Backward-compatible `NativeSymbol.export()` convenience factory preserved

### CLI Improvements
- Native hook menu now offers export-based or address-based targeting
- Optional wait-for-load prompt (when library is specified)
- Optional setTimeout delay prompt
- Empty library name input correctly produces `null` in generated JS

### Documentation
- Added Acknowledgements section to README
- Added examples for all native hook modes (null lib, address, waitForLoad, setTimeout)

### Testing
- 56 tests total (up from 45)
- New tests: null library, empty library, address-based, waitForLoad, setTimeout, combined wrappers, builder validation

## [3.0.0] - 2026-02-17

### Architecture Refactor
- **Breaking**: Moved entry point from `com.amrts.fridahelper.Main` to `com.amrts.fridahelper.cli.FridaHelperCli`
- Separated codebase into `core` (zero-I/O) and `cli` (thin adapter) packages
- Replaced all global mutable static state with immutable model classes
- Introduced `ScriptGenerator` interface for extensible hook type support

### New Features
- **Native hook generation**: `Interceptor.attach` scripts from library name + export symbol + arg count
- `NativeHookGenerator` produces ready-to-use Frida native hook scripts
- `ScriptWrapper` utility for optional `Java.perform()` wrapping (snippet vs script mode)
- Deterministic parameter name generation (a, b, c, ..., z, a0, b0, ...)
- Central `FridaHelperVersion` constant for version tracking

### Improvements
- `SmaliSignatureParser`: pure-function parser extracted from `JavaCls.match()`
- `ParamTypeResolver`: handles all JVM type descriptors including multi-dimensional arrays
- `ObfuscationDetector`: extracted from `Main.isObf()` as a reusable utility
- Proper error handling with exceptions instead of recursive retry loops
- CLI uses iterative loop instead of recursive `showGui()` calls

### Removed
- `Main.java` (replaced by `FridaHelperCli`)
- `JavaCls.java` (replaced by `SmaliSignatureParser`)
- `ParamExtractor.java` (replaced by `ParamTypeResolver`)
- `CharCount.java` (replaced by `ParamNameGenerator`)

### Testing
- JUnit 4 test suite covering parsers, generators, and utilities
- Tests include exact expected-output assertions for both Java and native hooks

### Build
- Replaced Android-only `build.gradle` with standard Java `application` plugin
- Added `settings.gradle` for proper Gradle project structure

## [2.0.0] - Original

- Initial release: Java hook script generation from smali signatures
- CLI-only, single-use-per-run, global static state
