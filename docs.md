# FridaHelper — Architecture & Code Reference

**Version:** 3.8.0 (code 11)
**Package:** `com.amrts.fridahelper`
**Min SDK:** 24 · **Target/Compile:** 34
**Language:** Java 8

## Modules

| Module | Purpose |
|--------|---------|
| `core` | Pure-Java library — parsing, generation, composition. No Android dependency. |
| `app`  | Android UI — MVVM with ViewModel + LiveData, Material 3, ViewPager2 tabs. |
| `cli`  | Headless CLI jar — same `core` generators, menu-driven terminal interface. |

## Package Structure

```
core/
  model/
    SmaliMethod        — Immutable parsed smali method (className, methodName, paramTypes, returnType)
    NativeSymbol       — Immutable native hook target (Builder pattern; export or address mode)
    HookRequest        — Tagged union (JAVA | NATIVE) carrying SmaliMethod or NativeSymbol
    GeneratedScript    — Result container (scriptText + hookType)
  parser/
    SmaliSignatureParser — Regex-based smali → SmaliMethod (pure, stateless)
    ParamTypeResolver    — JVM type descriptor → Java type name (e.g. "Ljava/lang/String;" → "java.lang.String")
  generator/
    ScriptGenerator    — Interface: generate(HookRequest) + generateBody(HookRequest)
    JavaHookGenerator  — Java.use + overload().implementation script; smart class/param variable naming
    NativeHookGenerator — Interceptor.attach / Module.findExportByName / waitForLoad (dynamic resolution inside onLibLoaded)
    ScriptWrapper      — Static wrappers: Java.perform, setTimeout, setImmediate
    ScriptComposer     — Merges N hooks → single script (groups same-class Java.use, top-level waitForLoad + wrapped bodies)
    CompositionOptions — Builder: wrapInPerform, setTimeoutMs
  batch/
    SmaliMethodEntry   — Data class: fullSignature + access flags (abstract, synthetic, bridge, constructor, native)
    SmaliFileReader    — Parses .smali files: extracts .class directive + .method entries
    BatchFilter        — Predicate chain: hard rules (abstract/synthetic/bridge skipped) + soft rules (skip constructors, class/method regex)
    BatchProcessor     — Orchestrates: read → filter → parse → generate composed script
  util/
    ParamNameGenerator — Type-aware param names (int→i, String→str; numbered on duplicates; abc fallback)
    ObfuscationDetector — Heuristic: non-ASCII (≥ U+0140) or short (< 3 chars) → obfuscated/unsuitable
  FridaHelperVersion   — Central VERSION constant ("3.8.0")

app/
  MainActivity         — AppCompat host: Toolbar + TabLayout + ViewPager2. Theme toggle only.
  HookPagerAdapter     — FragmentStateAdapter (page 0 = Java, page 1 = Native)
  HookViewModel        — Shared ViewModel: single-thread ExecutorService, LiveData streams (java/native/composed/queue/error), generation guard
  JavaHookFragment     — Input: smali signature + timeout + wrapInPerform. Output: generated script.
  NativeHookFragment   — Input: lib/export/address + argCount + timeout + waitForLoad + wrapInPerform.
  HookQueueAdapter     — RecyclerView adapter with DiffUtil for multi-hook queue display.
  BatchImportDialogHelper — MaterialAlertDialog for batch import: path input + filter toggles (skip constructors, class/method regex).
  ComposeDialogHelper  — MaterialAlertDialog collecting CompositionOptions before compose.
  ScriptOutputHelper   — Shared output display/copy/export logic with syntax highlighting and wrap toggle.
  JsSyntaxHighlighter  — Regex-based JS syntax highlighter (keywords, strings, template literals, Frida API, comments, numbers).
  ScriptExporter       — Saves script to Documents via MediaStore (API 29+) or external storage (24–28).
  ThemeManager         — Light / Dark / System toggle, persisted in SharedPreferences.

cli/
  FridaHelperCli       — Entry point: banner, menu loop
  CliMenuHandler       — Interactive menu: parse, generate, queue, compose, print
```

## Data Flow

```
User input (smali / native params)
  → Fragment validates input
  → HookViewModel.generateJavaHook() or generateNativeHook()
    → ExecutorService background thread
      → SmaliSignatureParser.parse() [Java only]
      → ScriptGenerator.generate(HookRequest)
      → ScriptWrapper.wrapIfNeeded() [optional]
    → LiveData.postValue(scriptText)
  → Fragment observes → showOutput()

Multi-hook:
  Fragment → viewModel.addHook(request)   — adds to ScriptComposer queue
  Fragment → viewModel.composeHooks(opts) — snapshot queue → ScriptComposer.compose() on bg thread
  Result → composedScriptOutput LiveData  — only active (resumed) fragment observes
```

## Key Design Decisions

- **Tagged union** (`HookRequest`) instead of inheritance — simpler for two variants in Java 8.
- **`generate()` vs `generateBody()`** — `generate()` produces full script with wrappers; `generateBody()` produces raw hook body for composition.
- **Top-level placement** — `waitForLoad` hooks define top-level `onLibLoaded`/`waitForLibLoading` functions; `Interceptor.attach` uses the dynamically resolved `nativeMethod` variable inside `onLibLoaded` callback.
- **Single-thread executor** in ViewModel — serializes generation, prevents races, no coroutines needed.
- **`isResumed()` guard** on composed output — prevents stale display in inactive ViewPager2 tab.
- **DiffUtil** in HookQueueAdapter — smooth animations instead of `notifyDataSetChanged()`.
- **Per-call SimpleDateFormat** in ScriptExporter — avoids thread-safety issues with static formatter.
- **ScriptOutputHelper** — shared utility class eliminates ~80 LOC duplication between JavaHookFragment and NativeHookFragment for output display, copy, and export.
- **CoordinatorLayout** — fragment root wraps ScrollView for proper Snackbar anchoring.
- **Editable script output (Option B)** — read-only by default; explicit Edit/Done toggle switches to EditText. Reset reverts to original generated script. Copy/Export always use currently visible text.
- **Smart variable naming** — class variable derived from simple class name (camelCased), falls back to `cls` if obfuscated or < 3 chars. Param names are type-aware (`i`, `str`, `b` …) with numbering only on duplicate types; unknown types fall back to `a, b, c`.
- **Template literal trace logging** — Single `console.log` per hook using JS template literals (`` console.log(`Class.method(${params}) => ${retval}`) ``). Uses simple class name for readable classes, full qualified name for obfuscated ones (varName == "cls").
- **Same-class Java.use deduplication** — ScriptComposer groups Java hooks by class name, emitting one `var cls = Java.use(...)` per class and generating only the method hooks under it.
- **Batch import with java.io.File** — Uses `java.io.File` instead of `java.nio.file.Path` for Android API 24+ compatibility. Recursive directory traversal via `File.listFiles()`.
- **Syntax highlighting via regex+Spannable** — Custom `JsSyntaxHighlighter` with priority-based token claiming (comments > strings > Frida API > keywords > numbers). Live editing uses `applyInPlace(Editable)` to update spans without `setText()` (avoids full layout pass). Dynamic debounce (30-300ms) based on script size.
- **NestedScrollView** — Replaced ScrollView with NestedScrollView in fragment layouts for proper RecyclerView measurement.

## Layouts

| File | Content |
|------|---------|
| `activity_main.xml` | MaterialToolbar + TabLayout + ViewPager2 |
| `fragment_java_hook.xml` | Smali input + timeout + switches + queue section + output |
| `fragment_native_hook.xml` | Radio (export/address) + fields + switches + queue + output |
| `item_hook_queue.xml` | Single queue item row with summary + remove button |
| `dialog_compose_options.xml` | CheckBox (wrapInPerform) + EditText (timeout) |

## Build

```
./gradlew assembleRelease     # Android APK → app/build/outputs/apk/release/
./gradlew :cli:jar            # CLI jar → cli/build/libs/
./gradlew :core:test          # Unit tests (SmaliParser, ParamType, JavaHook, NativeHook, Composer)
```

Signing uses `keystore.properties` at project root (not committed).
ProGuard enabled for release with `proguard-rules.pro`.
