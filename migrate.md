# Kotlin + Jetpack Compose Migration Plan — Android App

**Date:** 2026-03-15
**Current:** Java 8 across all modules, XML View system, MVVM with LiveData
**Target:** Kotlin + Jetpack Compose (app module only). Core and CLI remain Java.

---

## 1. Current Codebase Inventory

| Module | Files | LOC  | Dependencies |
|--------|-------|------|-------------|
| `core` | 15 Java | ~1750 | Pure Java 8, zero Android deps |
| `app`  | 15 Java + 6 XML layouts | ~1710 | AndroidX, Material 3, LiveData, ViewPager2, RecyclerView |
| `cli`  | 2 Java | ~410 | Core + java.util.Scanner |
| Tests  | 9 Java | ~1890 | JUnit 4 |
| **Total** | **41 Java + 6 XML** | **~5760** | |

### App Layout Files
- `activity_main.xml` — Toolbar + TabLayout + ViewPager2
- `fragment_java_hook.xml` — Signature input, switches, queue, output section
- `fragment_native_hook.xml` — Radio group, fields, switches, queue, output section
- `dialog_batch_import.xml` — Path + filter toggles
- `dialog_compose_options.xml` — Perform + timeout checkboxes
- `item_hook_queue.xml` — Queue item row

### App Java Classes
- `MainActivity` — AppCompat host with toolbar, tabs, theme toggle
- `HookPagerAdapter` — FragmentStateAdapter (2 pages)
- `HookViewModel` — SharedViewModel: single-thread executor, LiveData, generation logic
- `JavaHookFragment` — Java hook tab UI + batch import
- `NativeHookFragment` — Native hook tab UI
- `HookQueueAdapter` — RecyclerView.Adapter + DiffUtil
- `BatchImportDialogHelper` — Dialog builder for batch import
- `ComposeDialogHelper` — Dialog builder for compose options
- `ScriptOutputHelper` — Shared output display/edit/copy/export + syntax highlighting
- `JsSyntaxHighlighter` — Regex-based JS highlighting
- `ScriptExporter` — MediaStore/file export
- `ThemeManager` — SharedPreferences theme persistence

---

### Why only the app?

**Core stays Java.** It's stable, tested (122 tests), has zero Android dependency, and Kotlin interops with Java seamlessly. Converting ~1750 LOC of core + ~1890 LOC of tests would be pure rewrite effort with zero user-facing value. The app can call core APIs from Kotlin without any issues.

**CLI stays Java.** Same reasoning — 2 files, 408 LOC, works fine.

---

## 2. Migration Strategy: Phased Approach

### Why phased (not big-bang)?
- Kotlin and Java interop seamlessly. Mixed projects work fine.
- Big-bang rewrites introduce regression risk with no incremental benefit.
- Each phase is independently testable and releasable.

### Phase Overview

| Phase | Scope | Risk | Effort | Value |
|-------|-------|------|--------|-------|
| 0 | Gradle + toolchain setup | Low | 1-2 hrs | Enables everything else |
| 1 | App ViewModel + helpers → Kotlin | Medium | 1 day | Coroutines, Flow, StateFlow |
| 2 | App UI → Jetpack Compose | High | 3-5 days | Modern UI, eliminate XML layouts |

---

## 3. Phase 0: Gradle + Toolchain Setup

**Goal:** Enable Kotlin compilation in the app module without changing any Java code.

### Steps
1. Add Kotlin plugin to root `build.gradle`:
   ```groovy
   plugins {
       id 'org.jetbrains.kotlin.android' version '2.0.x' apply false
   }
   ```
2. Apply Kotlin Android plugin in `app/build.gradle`:
   ```groovy
   plugins {
       id 'org.jetbrains.kotlin.android'
   }
   ```
3. Set Kotlin JVM target to 1.8 (matching current Java target)
4. Add `kotlin-stdlib` dependency
5. Verify all 122 existing tests still pass
6. For Compose (Phase 2), add to `app/build.gradle`:
   ```groovy
   buildFeatures {
       compose true
   }
   composeOptions {
       kotlinCompilerExtensionVersion '1.5.x'
   }
   ```

**Deliverable:** App module compiles with Kotlin support enabled. Zero code changes. Core and CLI untouched.

---

## 4. Phase 1: App ViewModel + Helpers → Kotlin

**Goal:** Convert non-UI app classes to Kotlin, adopt Coroutines + StateFlow.

### Classes to Convert
1. `HookViewModel` → Kotlin + Coroutines
   - Replace `ExecutorService` with `viewModelScope.launch(Dispatchers.IO) { ... }`
   - Replace `MutableLiveData` with `MutableStateFlow` (or keep LiveData for now)
   - Replace `postValue()` with `emit()` or `value =`
2. `ScriptOutputHelper` → Kotlin
3. `JsSyntaxHighlighter` → Kotlin (Regex becomes cleaner with raw strings)
4. `ScriptExporter` → Kotlin
5. `ThemeManager` → Kotlin
6. `BatchImportDialogHelper` → Kotlin
7. `ComposeDialogHelper` → Kotlin
8. `HookQueueAdapter` → Kotlin (stays RecyclerView.Adapter until Phase 2 replaces it with LazyColumn)

### LiveData vs StateFlow Decision

| Aspect | LiveData | StateFlow |
|--------|----------|-----------|
| Lifecycle-aware | Yes (built-in) | Needs `collectAsStateWithLifecycle()` |
| Compose integration | `.observeAsState()` | Native `collectAsState()` |
| Null initial | Allowed | Required initial value |
| Testing | `InstantTaskExecutorRule` | `runTest {}` + Turbine |

**Recommendation:** If going to Compose (Phase 2), switch to `StateFlow` now. If staying with XML temporarily, keep `LiveData` until Phase 2.

### Coroutine Migration

```kotlin
// Before (Java):
private val executor = Executors.newSingleThreadExecutor()
executor.execute(() -> {
    // background work
    liveData.postValue(result);
});

// After (Kotlin):
viewModelScope.launch(Dispatchers.IO) {
    // background work
    _state.value = result
}
```

**Deliverable:** ViewModel and helpers in Kotlin. Fragments still Java (consuming Kotlin LiveData/StateFlow works).

---

## 5. Phase 2: App UI → Jetpack Compose

**Goal:** Replace XML layouts + Fragments with Compose UI. This is the largest phase.

### Approach: Screen-by-Screen Migration

Compose supports incremental adoption. Strategy:
1. Add Compose to existing `MainActivity` via `ComposeView` (or convert `MainActivity` to `ComponentActivity` with `setContent {}`)
2. Replace one screen at a time

### Migration Order

#### 4a. Navigation Scaffold
- Replace `TabLayout + ViewPager2 + FragmentStateAdapter` with Compose `Scaffold + TabRow + HorizontalPager`
- `HookPagerAdapter` → deleted

#### 4b. Java Hook Screen
- Replace `fragment_java_hook.xml` + `JavaHookFragment` with `@Composable fun JavaHookScreen(viewModel: HookViewModel)`
- Compose equivalents:
  | XML Widget | Compose |
  |-----------|---------|
  | `TextInputLayout + TextInputEditText` | `OutlinedTextField` |
  | `MaterialSwitch` | `Switch` |
  | `MaterialButton` | `Button`, `OutlinedButton`, `TextButton` |
  | `RecyclerView + Adapter + DiffUtil` | `LazyColumn` (automatic diffing) |
  | `ScrollView` / `NestedScrollView` | `verticalScroll()` modifier |
  | `Snackbar` | `SnackbarHost` |
  | `AlertDialog` (batch import) | `AlertDialog` composable |

#### 4c. Native Hook Screen
- Same approach as 4b
- Replace `fragment_native_hook.xml` + `NativeHookFragment`
- `RadioGroup` → Compose `Row { RadioButton }` or `SegmentedButton`

#### 4d. Script Output Component
- Replace `ScriptOutputHelper` (imperative View manipulation) with a `@Composable fun ScriptOutput(...)`
- This is the most complex Compose component:
  - Read-only highlighted text → `AnnotatedString` with `SpanStyle`
  - Edit mode → `BasicTextField` with `VisualTransformation` for highlighting
  - Wrap toggle → `Modifier.horizontalScroll()` conditional
  - Copy/Export → side effects via `LaunchedEffect` or button callbacks

#### 4e. Dialogs
- `BatchImportDialogHelper` → `@Composable fun BatchImportDialog(...)`
- `ComposeDialogHelper` → `@Composable fun ComposeOptionsDialog(...)`

#### 4f. Theme
- `ThemeManager` → Compose `MaterialTheme` with dynamic color
- `colors.xml` / `colors-night.xml` → Kotlin color definitions or `dynamicLightColorScheme` / `dynamicDarkColorScheme`

### Syntax Highlighting in Compose

Replace `SpannableStringBuilder + ForegroundColorSpan` with Compose `AnnotatedString + SpanStyle`:

```kotlin
fun highlightJs(code: String): AnnotatedString = buildAnnotatedString {
    append(code)
    // Apply SpanStyle(color = ...) for each regex match
    KEYWORDS_REGEX.findAll(code).forEach { match ->
        addStyle(SpanStyle(color = keywordColor), match.range.first, match.range.last + 1)
    }
    // ... comments, strings, etc.
}
```

For live editing, use `BasicTextField` with a custom `VisualTransformation` that applies highlighting on every recomposition (Compose handles diffing efficiently).

### What Gets Deleted

| File | Reason |
|------|--------|
| `fragment_java_hook.xml` | Replaced by Compose screen |
| `fragment_native_hook.xml` | Replaced by Compose screen |
| `activity_main.xml` | Replaced by Compose Scaffold |
| `item_hook_queue.xml` | Replaced by LazyColumn item composable |
| `dialog_batch_import.xml` | Replaced by Compose AlertDialog |
| `dialog_compose_options.xml` | Replaced by Compose AlertDialog |
| `JavaHookFragment.java` | Logic moves to Compose screen + ViewModel |
| `NativeHookFragment.java` | Same |
| `HookPagerAdapter.java` | Replaced by Compose HorizontalPager |
| `HookQueueAdapter.java` | Replaced by LazyColumn |
| `ScriptOutputHelper.java` | Replaced by Compose component |
| `BatchImportDialogHelper.java` | Replaced by Compose dialog |
| `ComposeDialogHelper.java` | Replaced by Compose dialog |

~13 files deleted, replaced by ~5-6 Compose files.

---

## 6. Dependencies to Add

```groovy
// Kotlin
implementation "org.jetbrains.kotlin:kotlin-stdlib:2.0.x"
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.x"

// Compose BOM (manages all Compose version alignment)
implementation platform("androidx.compose:compose-bom:2024.x.x")
implementation "androidx.compose.material3:material3"
implementation "androidx.compose.ui:ui"
implementation "androidx.compose.ui:ui-tooling-preview"
debugImplementation "androidx.compose.ui:ui-tooling"

// Compose integration
implementation "androidx.activity:activity-compose:1.9.x"
implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.x"
implementation "androidx.lifecycle:lifecycle-runtime-compose:2.8.x"

// Navigation (optional, if we add nav later)
implementation "androidx.navigation:navigation-compose:2.8.x"
```

---

## 7. Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Compose learning curve | Phase 2 can be deferred; app works fine with Kotlin + XML |
| APK size increase from Kotlin stdlib + Compose | ~2-3 MB increase. Acceptable for a dev tool. ProGuard/R8 mitigates. |
| minSdk 24 compatibility | Compose requires minSdk 21. No issue. |
| Build time increase | Kotlin + Compose adds ~5-10s to build. Manageable. |
| Syntax highlighting perf in Compose | `VisualTransformation` runs on recomposition — efficient for our script sizes (<50KB) |

---

## 8. Estimated Timeline

| Phase | Duration | Prerequisite |
|-------|----------|-------------|
| Phase 0: Gradle setup | 1-2 hours | None |
| Phase 1: ViewModel + Helpers → Kotlin | 1 day | Phase 0 |
| Phase 2: UI → Compose | 3-5 days | Phase 1 |
| **Total** | **~5-7 days** | |

Each phase ends with a passing test suite and a working release build.

---

## 9. Decision: Should We Migrate?

### Arguments For
- **Kotlin is the official Android language.** Google has been "Kotlin-first" since 2019. New Android APIs and documentation are Kotlin-only.
- **Jetpack Compose eliminates XML boilerplate.** Our 6 layout files + 3 adapter/helper classes become ~5 Compose files.
- **Better developer experience.** Coroutines replace ExecutorService, StateFlow replaces LiveData, null safety.
- **Future-proofing.** The Java View system is in maintenance mode. New Material 3 components are Compose-first.
- **Code reduction.** Estimated 20-30% less code in app (no adapters, no XML, no fragment lifecycle boilerplate).

### Arguments Against
- **It works fine as-is.** The app is functional and tested. Migration adds zero user-facing features.
- **5-7 days of effort** with regression risk for a tool that already works.
- **One developer.** The Kotlin/Compose benefits (team productivity, onboarding) matter less for a solo project.

### Recommendation
**Do it alongside a feature.** When the next significant UI feature is needed, migrate the app to Kotlin + Compose as part of that work. This avoids a pure rewrite with no user value. Core and CLI stay Java — they work, they're tested, and Kotlin interops with them seamlessly.
