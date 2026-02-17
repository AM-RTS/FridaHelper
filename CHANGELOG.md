# Changelog

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
