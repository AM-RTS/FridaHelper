# FridaHelper

A modular Java/Kotlin tool for generating [Frida](https://frida.re/) hook scripts from smali method signatures (Java hooks) and native library symbols (native hooks).

## Features

- **Java Hook Generation** — Paste a smali method signature, get a ready-to-use Frida `Java.perform` hook script with a concise trace log.
- **Native Hook Generation** — Export-based or address-based `Interceptor.attach` scripts with optional library wait and setTimeout.
- **Smart Variable Naming** — Generated scripts use meaningful names derived from class and type info (e.g. `networkManager` instead of `cls`, `str` instead of `a`). Obfuscated classes use the last two package segments (e.g. `example_a`) with collision-avoidance suffixes in composed scripts.
- **Batch Import** — Import `.smali` files or entire directories. Methods are parsed, filtered (abstract/synthetic/bridge auto-skipped), and queued for composition. Same-class hooks are grouped under a single `Java.use` call.
- **Concise Trace Logging** — Generated hooks use a single template-literal `console.log` line per method (e.g. `` console.log(`ClassName.method(${params}) => ${retval}`) ``) instead of verbose per-parameter logs.
- **Stack Trace Logging** — Optional per-hook stack trace via `android.util.Log.getStackTraceString` (Java) or `Thread.backtrace` (Native). Helper functions are defined once per script and called conditionally.
- **Multi-Hook Composition** — Queue multiple Java and Native hooks and compose them into a single script with shared wrappers, deduplicated `Java.use` declarations, and grouped `waitForLoad` hooks by library.
- **JavaScript Syntax Highlighting** — Generated scripts are syntax-highlighted in the Android app (keywords, strings, Frida API, comments, numbers, template literals). Uses `VisualTransformation` for lag-free editing.
- **Wrap Text Toggle** — Switch between horizontal-scrolling and word-wrapped display in both read-only and edit modes.
- **Custom Export Directory** — Configure a custom path for exported scripts via Settings; falls back to `Documents/FridaHelper`.
- **Modular Core** — The `core` package has zero I/O dependencies and can be embedded in any Java/Android application.
- **Android App** — Jetpack Compose Material 3 UI with tabbed Java/Native hook generation, hook queue, batch import, script export, and theme toggle.
- **CLI Interface** — Interactive command-line tool for quick script generation.

## Build

Requires Java 8+ and Gradle (or use the wrapper if available).

```bash
# Build the project
gradle build

# Run tests
gradle test

# Run the CLI
gradle run --console=plain
```

Or build a JAR and run directly:

```bash
gradle jar
java -jar cli/build/libs/cli-4.0.0.jar
```

## Usage

### CLI

```
FridaHelper 4.0.0
Options:
1. Java Hook (from smali signature)
2. Native Hook (lib + symbol / address)
3. About
4. Exit
```

### Java Hook Example

Input smali signature:
```
Lcom/example/Foo;->bar(ILjava/lang/String;)V
```

Output (script mode):
```javascript
Java.perform(function(){
    var foo = Java.use("com.example.Foo");
    foo.bar.overload("int", "java.lang.String").implementation = function(i, str){
        this.bar(i, str);
        console.log(`Foo.bar(${i}, ${str})`);
    }
});
```

### Native Hook Examples

#### Export-based (standard)

Input: `libfoo.so`, export `secret_func`, 2 arguments.

```javascript
Interceptor.attach(Module.findExportByName("libfoo.so", "secret_func"), {
    onEnter: function(args) {
        console.log("[*] Called secret_func");
        console.log("Arg 0: " + args[0]);
        console.log("Arg 1: " + args[1]);
    },
    onLeave: function(retval) {
        console.log("Return: " + retval);
    }
});
```

#### Null library (wildcard resolve)

When the library name is left empty, it resolves the export across all loaded modules:

```javascript
Interceptor.attach(Module.findExportByName(null, "open"), {
    onEnter: function(args) {
        console.log("[*] Called open");
        console.log("Arg 0: " + args[0]);
    },
    onLeave: function(retval) {
        console.log("Return: " + retval);
    }
});
```

#### Address-based hook

Hook a raw pointer directly:

```javascript
Interceptor.attach(ptr("0xDEAD"), {
    onEnter: function(args) {
        console.log("[*] Called 0xDEAD");
        console.log("Arg 0: " + args[0]);
    },
    onLeave: function(retval) {
        console.log("Return: " + retval);
    }
});
```

#### Wait for library loading

For libraries that aren't loaded yet at injection time (avoids "Expected a pointer" errors). Works with both export-based and address-based hooks:

```javascript
function onLibLoaded(libName) {
    var nativeMethod = Module.findExportByName(libName, "Jniint");
    Interceptor.attach(nativeMethod, {
        onEnter: function(args) {
            console.log("[*] Called Jniint");
        },
        onLeave: function(retval) {
            console.log("Return: " + retval);
        }
    });
}

function waitForLibLoading(libraryName) {
    var isLibLoaded = false;

    Interceptor.attach(Module.findExportByName(null, "android_dlopen_ext"), {
        onEnter: function(args) {
            var libraryPath = Memory.readCString(args[0]);
            if (libraryPath.includes(libraryName)) {
                console.log("[+] Loading library " + libraryPath + "...");
                isLibLoaded = true;
            }
        },
        onLeave: function(retval) {
            if (isLibLoaded) {
                onLibLoaded(libraryName);
                isLibLoaded = false;
            }
        }
    });
}

waitForLibLoading("native-lib.so");
```

#### setTimeout delayed execution

For hooks that need a delay after injection:

```javascript
setTimeout(function() {
    Interceptor.attach(Module.findExportByName("libfoo.so", "func"), {
        onEnter: function(args) {
            console.log("[*] Called func");
        },
        onLeave: function(retval) {
            console.log("Return: " + retval);
        }
    });
}, 500);
```

## Release Signing

The release build is configured to sign the APK when a `keystore.properties` file is present.

### 1. Generate a keystore

```bash
keytool -genkey -v \
  -keystore fridahelper-release.jks \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias fridahelper
```

### 2. Create `keystore.properties`

Copy the example and fill in your credentials:

```bash
cp keystore.properties.example keystore.properties
```

```properties
storeFile=fridahelper-release.jks
storePassword=your_store_password
keyAlias=fridahelper
keyPassword=your_key_password
```

> **Important:** Never commit `keystore.properties` or `.jks` files. They are in `.gitignore`.

### 3. Build the signed release APK

```bash
gradle :app:assembleRelease
```

The signed APK will be at `app/build/outputs/apk/release/app-release.apk`.

If no `keystore.properties` is found, the release build falls back to an unsigned APK.

## Architecture

```
com.amrts.fridahelper/
  core/                    # Zero-I/O reusable Java module
    FridaHelperVersion     # Central version constant
    model/                 # Immutable data classes
      SmaliMethod          # Parsed smali method signature
      NativeSymbol         # Native hook target (builder pattern)
      HookRequest          # Tagged union: Java | Native
      GeneratedScript      # Result wrapper
    parser/                # Smali signature + type descriptor parsing
    generator/             # ScriptGenerator interface + implementations
      JavaHookGenerator    # Java.use / overload / implementation (smart class+param naming)
      NativeHookGenerator  # Interceptor.attach (export / address / waitForLoad / setTimeout)
      ScriptWrapper        # Java.perform / setTimeout wrapping
      ScriptComposer       # Merges N hooks into single script (groups same-class Java.use, deduplicates waitForLoad by lib)
      CompositionOptions   # Wrapper config for composition
    batch/
      SmaliMethodEntry     # Parsed method entry with access flags
      SmaliFileReader      # .smali file/directory parser
      BatchFilter          # Predicate filtering (abstract/synthetic/bridge skipped)
      BatchProcessor       # Orchestrates batch pipeline (CLI-only, uses java.nio.file.Path)
    util/
      ParamNameGenerator   # Type-aware param names (int→i, String→str; abc fallback)
      ObfuscationDetector  # Non-ASCII / short-name detection for variable suitability
      ScriptIndent         # Shared indentation utility for generated scripts
  app/                     # Android UI (Kotlin, Jetpack Compose, Material 3)
    MainActivity           # ComponentActivity with enableEdgeToEdge(), Compose theme
    HookViewModel          # Shared ViewModel with StateFlow/SharedFlow, coroutines
    ThemeManager           # Light / Dark / System toggle + auto-scroll + export dir (SharedPreferences)
    ScriptExporter         # Save scripts to Documents or custom directory
    ui/
      FridaHelperApp       # Scaffold + TopAppBar + TabRow + HorizontalPager + Settings menu
      JavaHookScreen       # Java hook tab (input, batch import, queue, compose, output)
      NativeHookScreen     # Native hook tab (export/address mode, waitForLoad, output)
      theme/
        Color.kt           # Green-derived tonal palette
        Theme.kt           # Light/Dark/Dynamic color schemes
        Type.kt            # Typography
      components/
        ScriptOutput       # Script display/edit/copy/export with syntax highlighting
        HookQueueList      # Collapsible hook queue with animations
        BatchImportDialog  # Batch import with SAF folder picker
  cli/                     # Thin CLI layer (all I/O lives here)
    FridaHelperCli         # Entry point with try-with-resources Scanner
    CliMenuHandler         # Interactive menu: parse, generate, queue, compose, print
```

The `core` package is designed to be reused in an Android app without modification. It has no dependencies on `java.util.Scanner`, `System.in`, or `System.out`.

## CI/CD (GitHub Actions)

The project includes a GitHub Actions workflow (`.github/workflows/android.yml`) that:

1. **On every push/PR to `main`:** Runs core JUnit tests, builds CLI JAR, builds debug + release APK
2. **On tag push (`v*`):** Creates a GitHub Release with all artifacts attached

### Setting up signing secrets

To produce **signed** release APKs in CI, add these secrets to your repo (Settings > Secrets and variables > Actions):

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore file |
| `STORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g. `fridahelper`) |
| `KEY_PASSWORD` | Key password |

To encode your keystore:

```bash
base64 -w 0 fridahelper-release.jks > keystore-base64.txt
```

Then paste the contents of `keystore-base64.txt` as the `KEYSTORE_BASE64` secret.

### Creating a release

```bash
git tag v4.0.0
git push origin v4.0.0
```

The workflow will automatically build, sign, and publish the release.

> **Without secrets configured**, the workflow still builds everything — the release APK will just be unsigned.

## Acknowledgements

Special thanks to the following people for their contributions and support:

- **Mahmud** — Help and contributions to the original FridaHelper.
- **Rahat** — Various help and support during development.
- **Vologhat** — The smali parameter regex pattern that powers the signature parser.

## License

MIT
