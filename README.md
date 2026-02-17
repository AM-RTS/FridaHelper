# FridaHelper

A modular Java tool for generating [Frida](https://frida.re/) hook scripts from smali method signatures (Java hooks) and native library symbols (native hooks).

## Features

- **Java Hook Generation** — Paste a smali method signature, get a ready-to-use Frida `Java.perform` hook script with parameter logging.
- **Native Hook Generation** — Specify a library name, export symbol, and argument count to get an `Interceptor.attach` script.
- **Modular Core** — The `core` package has zero I/O dependencies and can be embedded in any Java/Android application.
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
java -jar build/libs/FridaHelper-3.0.0.jar
```

## Usage

### CLI

```
FridaHelper 3.0
Options:
1. Java Hook (from smali signature)
2. Native Hook (lib + symbol)
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
  var cls = Java.use("com.example.Foo");
   cls.bar.overload("int", "java.lang.String").implementation = function(a, b){
		console.log("Param 1: " + a);
console.log("Param 2: " + b);
   var retval = this.bar(a, b);
		console.log("Return Value: " + retval);
	//console.log(Java.use("android.util.Log").getStackTraceString(Java.use("java.lang.Exception").$new())),
   return retval;
   }
});
```

### Native Hook Example

Input: `libfoo.so`, export `secret_func`, 2 arguments.

Output:
```javascript
Interceptor.attach(Module.getExportByName("libfoo.so", "secret_func"), {
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

## Architecture

```
com.amrts.fridahelper/
  core/                    # Zero-I/O reusable module
    model/                 # Immutable data classes
    parser/                # Smali signature + type descriptor parsing
    generator/             # ScriptGenerator interface + implementations
    util/                  # Param naming, obfuscation detection
  cli/                     # Thin CLI layer (all I/O lives here)
```

The `core` package is designed to be reused in an Android app without modification. It has no dependencies on `java.util.Scanner`, `System.in`, or `System.out`.

**Note on module structure:** Currently `core` and `cli` are packages within a single Gradle module. This is intentional at the current scale (~600 LOC). The dependency direction is strictly one-way (`cli` -> `core`, never reverse), so extracting `core` into a separate Gradle module (`:core`) for Android reuse is a trivial refactor:

1. Create `core/build.gradle` with `plugins { id 'java-library' }`
2. Move `core/` sources into `core/src/main/java/...`
3. Add `implementation project(':core')` to the CLI and Android app modules
4. Update `settings.gradle` to include both

## Android Integration (MVVM)

When ready to build the Android app:

```
settings.gradle:
  include ':core', ':app'

app/build.gradle:
  implementation project(':core')
```

Your `ViewModel` calls `ScriptGenerator.generate(request)` and exposes the result via `LiveData<GeneratedScript>`. Fragments observe and display. The `core` module is tested with plain JUnit — no Android instrumentation needed.

## License

MIT
