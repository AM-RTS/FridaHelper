# FridaHelper — Architecture & Code Reference

**Version:** 3.3.1 (code 4)
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
    JavaHookGenerator  — Java.use + overload().implementation script
    NativeHookGenerator — Interceptor.attach / Module.findExportByName / waitForLoad script
    ScriptWrapper      — Static wrappers: Java.perform, setTimeout, setImmediate
    ScriptComposer     — Merges N hooks → single script (top-level waitForLoad + wrapped bodies)
    CompositionOptions — Builder: wrapInPerform, setTimeoutMs
  util/
    ParamNameGenerator — a,b,c… parameter names (ThreadLocal counter)
    ObfuscationDetector — Heuristic: single-letter class/method → likely obfuscated
  FridaHelperVersion   — Central VERSION constant ("3.3.1")

app/
  MainActivity         — AppCompat host: Toolbar + TabLayout + ViewPager2. Theme toggle only.
  HookPagerAdapter     — FragmentStateAdapter (page 0 = Java, page 1 = Native)
  HookViewModel        — Shared ViewModel: single-thread ExecutorService, LiveData streams (java/native/composed/queue/error), generation guard
  JavaHookFragment     — Input: smali signature + timeout + wrapInPerform. Output: generated script.
  NativeHookFragment   — Input: lib/export/address + argCount + timeout + waitForLoad + wrapInPerform.
  HookQueueAdapter     — RecyclerView adapter with DiffUtil for multi-hook queue display.
  ComposeDialogHelper  — MaterialAlertDialog collecting CompositionOptions before compose.
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
- **Top-level placement** — `waitForLoad` hooks define top-level functions, emitted before wrapped section.
- **Single-thread executor** in ViewModel — serializes generation, prevents races, no coroutines needed.
- **`isResumed()` guard** on composed output — prevents stale display in inactive ViewPager2 tab.
- **DiffUtil** in HookQueueAdapter — smooth animations instead of `notifyDataSetChanged()`.
- **Per-call SimpleDateFormat** in ScriptExporter — avoids thread-safety issues with static formatter.

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
