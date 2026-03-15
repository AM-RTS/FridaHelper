# FridaHelper — Architecture & Code Reference

**Version:** 4.0.0 (code 12)
**Package:** `com.amrts.fridahelper`
**Min SDK:** 24 · **Target/Compile:** 34
**Language:** Java 8 (core/cli), Kotlin (app)

## Modules

| Module | Purpose |
|--------|---------|
| `core` | Pure-Java library — parsing, generation, composition. No Android dependency. |
| `app`  | Android UI — Kotlin + Jetpack Compose, Material 3, MVVM with ViewModel + StateFlow. |
| `cli`  | Headless CLI jar — same `core` generators, menu-driven terminal interface. |

## Package Structure

```
core/
  model/
    SmaliMethod        — Immutable parsed smali method (className, methodName, paramTypes, returnType)
    NativeSymbol       — Immutable native hook target (Builder pattern; export or address mode, waitForLoad in both)
    HookRequest        — Tagged union (JAVA | NATIVE) carrying SmaliMethod or NativeSymbol
    GeneratedScript    — Result container (scriptText + hookType)
  parser/
    SmaliSignatureParser — Regex-based smali → SmaliMethod (pure, stateless)
    ParamTypeResolver    — JVM type descriptor → Java type name (e.g. "Ljava/lang/String;" → "java.lang.String")
  generator/
    ScriptGenerator    — Interface: generate(HookRequest) + generateBody(HookRequest)
    JavaHookGenerator  — Java.use + overload().implementation script; smart class/param variable naming; stack trace support
    NativeHookGenerator — Interceptor.attach / Module.findExportByName / waitForLoad (dynamic resolution for both export & address modes)
    ScriptWrapper      — Static wrappers: Java.perform, setTimeout, setImmediate
    ScriptComposer     — Merges N hooks → single script (groups same-class Java.use, deduplicates waitForLoad by library, collision-safe variable names)
    CompositionOptions — Builder: wrapInPerform, setTimeoutMs, enableStackTrace
  batch/
    SmaliMethodEntry   — Data class: fullSignature + access flags (abstract, synthetic, bridge, constructor, native)
    SmaliFileReader    — Parses .smali files: extracts .class directive + .method entries
    BatchFilter        — Predicate chain: hard rules (abstract/synthetic/bridge skipped) + soft rules (skip constructors, class/method regex)
    BatchProcessor     — Orchestrates batch pipeline (CLI-only, uses java.nio.file.Path)
  util/
    ParamNameGenerator — Type-aware param names (int→i, String→str; numbered on duplicates; abc fallback). Array API avoids split round-trips.
    ObfuscationDetector — Heuristic: non-ASCII (≥ U+0140) or short (< 3 chars) → obfuscated/unsuitable
    ScriptIndent       — Shared indentation utility for generated scripts (used by ScriptComposer, ScriptWrapper, NativeHookGenerator)
  FridaHelperVersion   — Central VERSION constant ("4.0.0")

app/
  MainActivity         — ComponentActivity with enableEdgeToEdge() + Compose theme. Manages theme state.
  HookViewModel        — Shared ViewModel: coroutines + Dispatchers.IO, StateFlow/SharedFlow for script outputs, error events, scroll events, auto-scroll setting
  ThemeManager         — Light / Dark / System toggle, auto-scroll, export directory — all persisted in SharedPreferences
  ScriptExporter       — Saves scripts to custom directory, Documents/FridaHelper (MediaStore API 29+), or app-specific storage (24–28). Custom filename support.
  ui/
    FridaHelperApp       — Scaffold + TopAppBar + SecondaryTabRow + HorizontalPager + Settings DropdownMenu (auto-scroll toggle, export directory)
    JavaHookScreen       — Java hook tab: smali input, timeout, wrapInPerform, enableStackTrace, batch import, queue, compose, output
    NativeHookScreen     — Native hook tab: export/address mode, hex validation, waitForLoad (both modes), stack trace, queue, compose, output
    theme/
      Color.kt           — Green-derived tonal palette (primary, secondary, tertiary containers, outline variants)
      Theme.kt           — Light/Dark/Dynamic color schemes with explicit tonal definitions
      Type.kt            — Typography
    components/
      ScriptOutput       — Script display/edit/copy/export with JS syntax highlighting (VisualTransformation), text selection, clear button, export filename dialog
      HookQueueList      — Collapsible hook queue (auto-collapse > 3 items) with AnimatedVisibility
      BatchImportDialog  — Batch import dialog with SAF folder picker (folder icon)

cli/
  FridaHelperCli       — Entry point: banner, menu loop (try-with-resources Scanner)
  CliMenuHandler       — Interactive menu: parse, generate, queue, compose, print. Stack trace prompt included.
```

## Data Flow

```
User input (smali / native params)
  → Screen validates input
  → HookViewModel.generateJavaHook() or generateNativeHook()
    → Dispatchers.IO coroutine
      → SmaliSignatureParser.parse() [Java only]
      → ScriptGenerator.generate(HookRequest)
      → ScriptWrapper.wrapIfNeeded() [optional]
    → StateFlow.value = scriptText (clears other outputs)
    → SharedFlow scrollToOutput emitted
  → Screen collects StateFlow → ScriptOutput composable

Multi-hook:
  Screen → viewModel.addHook(request)    — adds to ScriptComposer queue
  Screen → viewModel.composeHooks(opts)  — snapshot queue → ScriptComposer.compose() on IO
  Result → composedScriptOutput StateFlow (clears single-hook outputs)

Batch import:
  BatchImportDialog → viewModel.importSmaliMethods(path, filters)
    → Dispatchers.IO: read + parse files → returns List<HookRequest>
    → Main thread: composer.addRequest() for each (avoids race condition)
```

## Key Design Decisions

- **Tagged union** (`HookRequest`) instead of inheritance — simpler for two variants in Java 8.
- **`generate()` vs `generateBody()`** — `generate()` produces full script with wrappers; `generateBody()` produces raw hook body for composition.
- **`generateWithoutHelpers()`** — NativeHookGenerator method for the composer to avoid duplicating `nativeLog` definitions.
- **Top-level placement** — `waitForLoad` hooks define top-level `onLibLoaded`/`waitForLibLoading` functions. Composer groups same-library hooks into a single `onLibLoaded` callback.
- **Stack trace helper outside Java.perform** — `log()` (Java) and `nativeLog()` (Native) are defined at top level; safe because they're called from within `Java.perform`/`Interceptor.attach` callbacks that run in the correct context.
- **SharedFlow for one-shot events** — Error messages and scroll-to-output events use `SharedFlow` instead of `StateFlow` to ensure every emission triggers a snackbar/scroll, even for identical consecutive errors.
- **All composer mutations on main thread** — `ScriptComposer` is not thread-safe; batch import returns parsed requests from IO and adds them to the composer on the main thread.
- **Variable collision avoidance** — `ScriptComposer` tracks `usedVarNames` per composition; `ensureUnique()` appends numeric suffixes on collision.
- **Obfuscated class naming** — Uses last 2 segments of package (e.g. `com.example.a` → `example_a`) instead of generic `cls`.
- **`collectAsStateWithLifecycle()`** — All StateFlow collections in Compose use lifecycle-aware collection for resource efficiency.
- **HorizontalPager swipe vs code scroll** — `userScrollEnabled = !hasVisibleScript` prevents accidental tab switches when scrolling code horizontally.
- **Auto-scroll opt-out** — Persisted in SharedPreferences. Auto-scroll only fires on explicit generate/compose events (SharedFlow), not on recomposition.
- **Custom export directory** — Stored in SharedPreferences via ThemeManager. ScriptExporter checks it first; empty falls back to MediaStore/app-storage.
- **Per-call SimpleDateFormat** in ScriptExporter — avoids thread-safety issues with static formatter.
- **AnimatedVisibility** — Script output and hook queue use tuned `tween` animations with M3 easing (FastOutSlowIn enter, FastOutLinearIn exit) and `it / 4` slide offsets.
- **Collapsible hook queue** — Auto-collapses when > 3 items; expandable via chevron.
- **SelectionContainer** — Code display wrapped for text selection support.

## Build

```
./gradlew assembleRelease     # Android APK → app/build/outputs/apk/release/
./gradlew :cli:jar            # CLI jar → cli/build/libs/
./gradlew :core:test          # Unit tests (SmaliParser, ParamType, JavaHook, NativeHook, Composer)
```

Signing uses `keystore.properties` at project root (not committed).
ProGuard enabled for release with `proguard-rules.pro`.
Gradle JVM: `-Xmx2048m -XX:MaxMetaspaceSize=512m` (configured in `gradle.properties`).
