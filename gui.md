# GUI Migration Plan: Jetpack Compose + Material 3

> **Scope**: Android `app` module only. `core` and `cli` remain Java.
> **Branch**: `kotlin-migration` (builds on completed Java→Kotlin conversion)

---

## Current State

| Aspect | Now | After |
|---|---|---|
| UI toolkit | XML layouts + View system | Jetpack Compose |
| Design system | Material 3 (via View-based Material lib) | Material 3 (Compose native) |
| Navigation | ViewPager2 + TabLayout + Fragments | HorizontalPager + TabRow (no Fragments) |
| State management | ViewModel + LiveData | ViewModel + StateFlow / Compose State |
| Theme | XML `styles.xml` + `colors.xml`, hardcoded palette | Compose `MaterialTheme`, dynamic color (API 31+), static fallback |
| Dialogs | `MaterialAlertDialogBuilder` (imperative) | `AlertDialog` composable (declarative) |
| Lists | RecyclerView + Adapter | `LazyColumn` |
| Snackbar | `Snackbar.make(view, ...)` | `SnackbarHost` + `SnackbarHostState` |
| Code output | `TextView`/`EditText` + `SpannableString` highlighting | `BasicTextField` + `AnnotatedString` / `VisualTransformation` |

---

## Gradle Setup (Phase 0)

**Important**: Since we use Kotlin 2.0.21, we use the **Compose Compiler Gradle Plugin** (not the deprecated `composeOptions.kotlinCompilerExtensionVersion`).

```groovy
// root build.gradle — add the Compose Compiler plugin
buildscript {
    dependencies {
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21'
    }
}

// app/build.gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.plugin.compose'  // Compose Compiler — matches Kotlin version automatically
}

android {
    compileSdk 35  // Required for latest Compose BOM

    buildFeatures {
        compose true
    }
    // No composeOptions block needed — the plugin handles compiler version automatically
}

dependencies {
    // Compose BOM — single version for all Compose libs (use latest stable)
    def composeBom = platform('androidx.compose:compose-bom:2025.05.00')
    implementation composeBom

    // Core Compose
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.foundation:foundation'
    debugImplementation 'androidx.compose.ui:ui-tooling'

    // Material 3
    implementation 'androidx.compose.material3:material3'

    // Activity Compose (setContent)
    implementation 'androidx.activity:activity-compose:1.9.0'

    // ViewModel + Compose integration
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-compose:2.7.0'  // for collectAsStateWithLifecycle()

    // Remove (no longer needed after full migration):
    //   appcompat, material (view), constraintlayout, fragment, viewpager2, recyclerview
}
```

---

## Architecture After Migration

```
app/src/main/java/com/amrts/fridahelper/app/
├── MainActivity.kt              ← setContent { FridaHelperApp() }
├── HookViewModel.kt             ← ViewModel (StateFlow instead of LiveData)
├── theme/
│   ├── Theme.kt                 ← MaterialTheme wrapper, dynamic color logic
│   ├── Color.kt                 ← Color palette (green hacker theme)
│   └── Type.kt                  ← Typography (monospace for code, default for UI)
├── ui/
│   ├── FridaHelperApp.kt        ← Scaffold + TopAppBar + TabRow + HorizontalPager
│   ├── JavaHookScreen.kt        ← Replaces JavaHookFragment
│   ├── NativeHookScreen.kt      ← Replaces NativeHookFragment
│   ├── components/
│   │   ├── ScriptOutput.kt      ← Code viewer/editor with highlighting
│   │   ├── HookQueueList.kt     ← LazyColumn for hook queue
│   │   ├── HookQueueItem.kt     ← Single queue row
│   │   ├── BatchImportDialog.kt ← AlertDialog composable
│   │   └── ComposeOptionsDialog.kt ← AlertDialog composable
│   └── util/
│       ├── JsSyntaxHighlighter.kt  ← Returns AnnotatedString
│       └── ScriptExporter.kt       ← (unchanged, no UI)
├── ThemeManager.kt              ← (keep, SharedPreferences logic)
└── ScriptExporter.kt            ← (keep, file I/O logic)
```

---

## Phase 1: Theme + Scaffold Shell

**Goal**: App boots into Compose with proper M3 theme, TopAppBar, TabRow. Screens are placeholder stubs.

### 1a. Theme Setup (`theme/`)

```kotlin
// Color.kt
val GreenPrimary = Color(0xFF1B5E20)
val GreenDark = Color(0xFF003300)
val GreenAccent = Color(0xFF4CAF50)
val CodeBg = Color(0xFF263238)
val CodeText = Color(0xFFA5D6A7)

val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    primaryContainer = GreenDark,
    secondary = GreenAccent,
    // ... other roles
)

val DarkColorScheme = darkColorScheme(
    primary = GreenAccent,
    primaryContainer = GreenDark,
    // ... other roles
)
```

```kotlin
// Theme.kt
@Composable
fun FridaHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = FridaTypography,
        content = content
    )
}
```

### 1b. Scaffold + Navigation (`ui/FridaHelperApp.kt`)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridaHelperApp(viewModel: HookViewModel = viewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("Java Hook", "Native Hook")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FridaHelper") },
                actions = {
                    // Theme toggle IconButton
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> JavaHookScreen(viewModel, snackbarHostState)
                    1 -> NativeHookScreen(viewModel, snackbarHostState)
                }
            }
        }
    }
}
```

### 1c. MainActivity Simplification

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()  // Modern edge-to-edge rendering
        super.onCreate(savedInstanceState)
        setContent {
            FridaHelperTheme {
                FridaHelperApp()
            }
        }
    }
}
```

**Edge-to-Edge**: `enableEdgeToEdge()` (from `activity-compose`) makes the app draw behind system bars. The `Scaffold` inner padding handles safe area insets automatically.

**Deletions after Phase 1**: `HookPagerAdapter.kt`, XML layouts (`activity_main.xml`), `styles.xml`, `colors.xml`, `menu_main.xml`.

---

## Phase 2: Java Hook Screen

**Goal**: Full Compose replacement of `JavaHookFragment`.

### Components:
- **Import button**: `FilledTonalButton` with icon
- **Signature input**: `OutlinedTextField` with error support via `isError` + `supportingText`
- **Timeout input**: `OutlinedTextField` with `KeyboardOptions(keyboardType = KeyboardType.Number)`
- **Full script toggle**: `Switch` (M3) with label `Row`
- **Generate / Add to Queue buttons**: `Row` with `Button` + `FilledTonalButton`, equal weight via `Modifier.weight(1f)`
- **Hook Queue**: `LazyColumn` items with swipe-to-delete (`SwipeToDismissBox`) or trailing `IconButton`
- **Compose / Clear buttons**: `Row` with `Button` + `OutlinedButton`
- **Script Output**: Custom `ScriptOutput` composable (see Phase 4)

### M3 Best Practices Applied:
| Old (View) | New (Compose M3) | Why |
|---|---|---|
| `TextInputLayout` + `TextInputEditText` | `OutlinedTextField` | Native M3 component, built-in error/label states |
| `MaterialSwitch` | `Switch()` | Compose M3, same visual |
| `MaterialButton` style variants | `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton` | Proper M3 hierarchy (primary → tonal → outlined → text) |
| `Snackbar.make(view, ...)` | `snackbarHostState.showSnackbar()` | Declarative, coroutine-based |
| `RecyclerView` + `Adapter` | `LazyColumn` | No adapter boilerplate |
| `MaterialAlertDialogBuilder` | `AlertDialog` composable | Declarative, state-driven |

---

## Phase 3: Native Hook Screen

**Goal**: Full Compose replacement of `NativeHookFragment`.

### Components:
- **Target mode selector**: `SingleChoiceSegmentedButtonRow` (stable M3). If we adopt M3 Expressive later, upgrade to `ButtonGroup` with connected shapes (`@ExperimentalMaterial3ExpressiveApi`).
- **Conditional fields**: `AnimatedVisibility` for export/address fields based on selected mode
- **Text fields**: Same `OutlinedTextField` pattern as Java Hook
- **Switches**: `Switch` for Wait for Load, Wrap in Java.perform
- **Queue + Output**: Shared composables from Phase 2

---

## Phase 4: Script Output Composable

**Goal**: Replace the complex `ScriptOutputHelper` (3 views: `TextView`, `TextView` wrapped, `EditText`) with a single Compose component.

### Design:

```kotlin
@Composable
fun ScriptOutput(
    script: String,
    onScriptChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var wrapping by remember { mutableStateOf(false) }

    // Modern: use TextFieldState (Compose Foundation 1.6+) instead of mutableStateOf + onValueChange
    // TextFieldState avoids "jumping cursor" bugs and async race conditions
    val textFieldState = rememberTextFieldState(script)

    Column(modifier) {
        // Header row: "Output" label + Wrap/Edit/Reset buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Output", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { wrapping = !wrapping }) { Text(if (wrapping) "No Wrap" else "Wrap") }
            TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done" else "Edit") }
            if (editing) {
                TextButton(onClick = { editBuffer = script }) { Text("Reset") }
            }
        }

        // Code area
        Surface(
            color = CodeBg,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (editing) {
                BasicTextField(
                    state = textFieldState,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = CodeText, fontSize = 12.sp),
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                        .then(if (!wrapping) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                )
            } else {
                val highlighted = remember(textFieldState.text) { highlightJs(textFieldState.text.toString()) }
                Text(
                    text = highlighted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                        .then(if (!wrapping) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                )
            }
        }

        // Copy + Export buttons
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { /* copy */ }, modifier = Modifier.weight(1f)) { Text("Copy") }
            OutlinedButton(onClick = { /* export */ }, modifier = Modifier.weight(1f)) { Text("Export") }
        }
    }
}
```

### Syntax Highlighting in Compose:

Two modes with different strategies:

**Read-only mode** — Use `AnnotatedString` with `Text`:
```kotlin
fun highlightJs(code: String): AnnotatedString {
    return buildAnnotatedString {
        append(code)
        // Apply SpanStyle(color = ...) for each regex match
        // Same token patterns as current JsSyntaxHighlighter
        // Uses addStyle() instead of ForegroundColorSpan
    }
}
// In composable: val highlighted = remember(script) { highlightJs(script) }
```

**Edit mode** — Use `VisualTransformation` with `BasicTextField`:
```kotlin
class JsSyntaxTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = highlightJs(text.text)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
// In composable: BasicTextField(visualTransformation = JsSyntaxTransformation())
```

**Why `VisualTransformation`**: Decouples the raw text (source of truth) from the presentation (colored text). The transformation only runs at render time, avoiding recomposition cascades during rapid typing. No debounce or `TextWatcher` equivalent needed — Compose handles this natively.

For very large scripts (>10KB), consider wrapping the transformation with a debounce via `snapshotFlow` + `derivedStateOf`.

---

## Phase 5: Dialogs

### Batch Import Dialog

```kotlin
@Composable
fun BatchImportDialog(
    onDismiss: () -> Unit,
    onImport: (path: String, filter: BatchFilter) -> Unit
) {
    var path by remember { mutableStateOf("") }
    var skipConstructors by remember { mutableStateOf(true) }
    var includeClasses by remember { mutableStateOf("") }
    var excludeMethods by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch Import .smali") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Path") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = skipConstructors, onCheckedChange = { skipConstructors = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Skip constructors")
                }
                OutlinedTextField(value = includeClasses, onValueChange = { includeClasses = it }, label = { Text("Include classes (regex)") })
                OutlinedTextField(value = excludeMethods, onValueChange = { excludeMethods = it }, label = { Text("Exclude methods (regex)") })
            }
        },
        confirmButton = { TextButton(onClick = { /* build filter, call onImport */ }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

### Compose Options Dialog
Same pattern — `AlertDialog` with `Checkbox` → `Switch`, `OutlinedTextField` for timeout.

---

## Phase 6: ViewModel Migration (LiveData → StateFlow)

```kotlin
class HookViewModel : ViewModel() {
    private val _javaScriptOutput = MutableStateFlow<String?>(null)
    val javaScriptOutput: StateFlow<String?> = _javaScriptOutput.asStateFlow()

    // ... same pattern for all LiveData fields

    // In Compose screens — ALWAYS use collectAsStateWithLifecycle (not collectAsState):
    // val output by viewModel.javaScriptOutput.collectAsStateWithLifecycle()
    // This stops collecting when the app backgrounds, saving resources
    // (critical when running alongside emulators/debuggers)
}
```

**Why StateFlow**: Better Kotlin integration, no lifecycle-livedata dependency needed, works natively with `collectAsStateWithLifecycle()`.

---

## Phase 7: Cleanup

**Delete after migration**:
- All XML layout files (`activity_main.xml`, `fragment_*.xml`, `dialog_*.xml`, `item_*.xml`)
- `menu_main.xml`
- `styles.xml`, `colors.xml` (migrated to Compose `theme/`)
- `drawable/` theme icons (replaced with Compose `Icons.Filled.*`)
- `HookPagerAdapter.kt` (no more ViewPager2)
- `HookQueueAdapter.kt` (no more RecyclerView)
- `BatchImportDialogHelper.kt` (replaced by composable)
- `ComposeDialogHelper.kt` (replaced by composable)
- `ScriptOutputHelper.kt` (replaced by composable)

**Keep (modified)**:
- `MainActivity.kt` — simplified to `setContent { FridaHelperTheme { FridaHelperApp() } }`
- `HookViewModel.kt` — LiveData → StateFlow
- `ThemeManager.kt` — SharedPreferences persistence logic stays
- `ScriptExporter.kt` — pure I/O, no UI
- `JsSyntaxHighlighter.kt` — refactored to return `AnnotatedString`

**Remove dependencies**:
```groovy
// Remove:
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
implementation 'androidx.fragment:fragment:1.6.2'
implementation 'androidx.viewpager2:viewpager2:1.0.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.7.0'
```

---

## Execution Order

| Step | What | Estimated Effort |
|---|---|---|
| 0 | Gradle: add Compose BOM + deps, `buildFeatures.compose = true` | Small |
| 1 | `theme/` package: Color.kt, Type.kt, Theme.kt | Small |
| 2 | `FridaHelperApp.kt`: Scaffold + TopAppBar + TabRow + HorizontalPager (stub screens) | Medium |
| 3 | `MainActivity.kt`: simplify to `setContent` | Small |
| 4 | `JavaHookScreen.kt`: full form + input validation + observers | Large |
| 5 | `NativeHookScreen.kt`: full form + mode switching | Large |
| 6 | `ScriptOutput.kt`: code view/edit + highlighting | Medium |
| 7 | `HookQueueList.kt` + `HookQueueItem.kt`: LazyColumn | Small |
| 8 | `BatchImportDialog.kt` + `ComposeOptionsDialog.kt` | Small |
| 9 | `HookViewModel.kt`: LiveData → StateFlow | Medium |
| 10 | Delete old files, remove View dependencies | Small |
| 11 | Test, fix edge cases, build release | Medium |

---

## UX Considerations

### Keyboard / IME Handling
- Apply `Modifier.imePadding()` to `Scaffold` content or the bottom-most `Column` so the UI shifts up when the keyboard appears (prevents "Generate" / "Add to Queue" buttons from being obscured).
- Use `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)` on sequential text fields to enable "Next" flow between signature → timeout → generate.
- Use `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)` for timeout and arg count fields.

### Back Press Behavior
- Use `BackHandler` composable (not manual `onBackPressed`): if on tab 2, first press goes to tab 1; second press exits.
- For Android 14+ predictive back gesture: consider `PredictiveBackHandler` for the system-wide "peek" animation.
- If a dialog is open, back press dismisses it (handled by `AlertDialog.onDismissRequest` automatically).

### Pager vs Code Scroll Conflict
- When the code editor is focused and the user scrolls horizontally through a wide line, the `HorizontalPager` should NOT intercept and switch tabs.
- Fix: Use `userScrollEnabled = !isCodeFocused` on the `HorizontalPager`, or use `Modifier.nestedScroll` to prioritize the code scroll container.

### Theme & Accessibility
- Use [Material Theme Builder](https://m3.material.io/theme-builder) to verify the green hacker palette provides sufficient contrast for all M3 color roles (especially `onPrimary`, `onSurface`).
- The code viewer keeps its dedicated dark palette (`CodeBg` / `CodeText`) regardless of dynamic color — it's a terminal, not a themed surface.
- When dynamic color is enabled (API 31+), the app chrome (toolbar, buttons, switches) uses the user's wallpaper colors while the code viewer stays consistent.

---

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Syntax highlighting performance in Compose `BasicTextField` | Read mode: `remember(code)` caches `AnnotatedString`. Edit mode: `VisualTransformation` runs only at render time, no recomposition cascade |
| Loss of `HorizontalScrollView` behavior for code | `Modifier.horizontalScroll(rememberScrollState())` provides equivalent |
| Dynamic color on API <31 | Fallback to static green theme; conditional logic in `Theme.kt` |
| ProGuard/R8 with Compose | Add Compose-specific keep rules if needed; Compose compiler usually handles this |
| `adjustPan` keyboard behavior | Compose handles this better natively; test on device |

---

## M3 Component Mapping Reference

| Current (XML/View) | Compose M3 Equivalent |
|---|---|
| `MaterialToolbar` | `TopAppBar` / `CenterAlignedTopAppBar` |
| `TabLayout` + `ViewPager2` | `SecondaryTabRow` + `HorizontalPager` |
| `TextInputLayout` + `TextInputEditText` | `OutlinedTextField` |
| `MaterialSwitch` | `Switch` |
| `MaterialButton` (filled) | `Button` |
| `MaterialButton` (tonal) | `FilledTonalButton` |
| `MaterialButton` (outlined) | `OutlinedButton` |
| `MaterialButton` (text) | `TextButton` |
| `RadioGroup` + `MaterialRadioButton` | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` (or `ButtonGroup` with M3 Expressive) |
| `CheckBox` | `Checkbox` |
| `RecyclerView` | `LazyColumn` |
| `MaterialAlertDialogBuilder` | `AlertDialog` composable |
| `Snackbar.make()` | `SnackbarHostState.showSnackbar()` |
| `CoordinatorLayout` | `Scaffold` |
| `NestedScrollView` | `Modifier.verticalScroll()` or `LazyColumn` |
| `ImageButton` | `IconButton` + `Icon` |
