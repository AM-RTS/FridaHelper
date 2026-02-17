package com.amrts.fridahelper.cli;

import com.amrts.fridahelper.core.FridaHelperVersion;
import com.amrts.fridahelper.core.generator.JavaHookGenerator;
import com.amrts.fridahelper.core.generator.NativeHookGenerator;
import com.amrts.fridahelper.core.generator.ScriptGenerator;
import com.amrts.fridahelper.core.generator.ScriptWrapper;
import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;
import com.amrts.fridahelper.core.model.SmaliMethod;
import com.amrts.fridahelper.core.parser.SmaliSignatureParser;

import java.util.Scanner;

/**
 * Handles CLI menu interactions. All I/O is confined to this class and
 * {@link FridaHelperCli}. The core module is invoked as pure function calls.
 *
 * Backward-compatible: option 1 follows the original FridaHelper 2.0 flow
 * (smali signature -> script/snippet choice -> output).
 */
public final class CliMenuHandler {

    private final Scanner scanner;
    private final SmaliSignatureParser parser;
    private final ScriptGenerator javaGenerator;
    private final ScriptGenerator nativeGenerator;

    public CliMenuHandler(Scanner scanner) {
        this.scanner = scanner;
        this.parser = new SmaliSignatureParser();
        this.javaGenerator = new JavaHookGenerator();
        this.nativeGenerator = new NativeHookGenerator();
    }

    /**
     * Runs the Java hook flow: reads smali signature, asks script/snippet mode, generates output.
     */
    public void handleJavaHook() {
        System.out.println("\nInput Your Method's Signature (in smali syntax): ");
        String signature = scanner.nextLine().trim();

        if (signature.isEmpty()) {
            System.out.println("Error: empty signature.");
            return;
        }

        SmaliMethod method;
        try {
            method = parser.parse(signature);
        } catch (IllegalArgumentException e) {
            System.out.println("\nError!: " + e.getMessage());
            System.out.println("Make sure its in smali syntax. Example: Lcom/example/Foo;->bar(I)V");
            return;
        }

        boolean fullScript = askScriptOrSnippet();
        HookRequest request = HookRequest.java(method);
        GeneratedScript result = javaGenerator.generate(request);

        if (fullScript) {
            result = ScriptWrapper.wrapIfNeeded(result);
        }

        String label = fullScript ? "script" : "snippet";
        System.out.println("\nNote: You may need to fix var retval");
        System.out.println("\n[*] Here's your frida " + label + "! :\n");
        System.out.println(result.getScriptText());
    }

    /**
     * Runs the native hook flow: reads lib name, export name, arg count, generates output.
     */
    public void handleNativeHook() {
        System.out.println("\nEnter library name (e.g. libfoo.so): ");
        String libName = scanner.nextLine().trim();
        if (libName.isEmpty()) {
            System.out.println("Error: library name cannot be empty.");
            return;
        }

        System.out.println("Enter export/symbol name (e.g. secret_func): ");
        String exportName = scanner.nextLine().trim();
        if (exportName.isEmpty()) {
            System.out.println("Error: export name cannot be empty.");
            return;
        }

        System.out.println("Enter number of arguments (0 if unknown): ");
        int argCount;
        try {
            argCount = Integer.parseInt(scanner.nextLine().trim());
            if (argCount < 0) {
                System.out.println("Error: argument count must be >= 0.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Error: please enter a valid integer.");
            return;
        }

        NativeSymbol symbol = new NativeSymbol(libName, exportName, argCount);
        HookRequest request = HookRequest.nativeHook(symbol);
        GeneratedScript result = nativeGenerator.generate(request);

        System.out.println("\n[*] Here's your native frida script! :\n");
        System.out.println(result.getScriptText());
    }

    /**
     * Displays the about message.
     */
    public void showAbout() {
        System.out.println("\n" + FridaHelperVersion.FULL);
        System.out.println("A tool to generate Frida hook scripts easily.");
        System.out.println("Supports: Java method hooks (from smali signatures) and Native hooks (Interceptor.attach).");
        System.out.println("\nGitHub: " + FridaHelperVersion.REPO_URL);
        System.out.println("Thanks to: Mahmud, BotXRahat & Vologhat for various contributions.\n");
    }

    /**
     * Asks the user whether to generate a full script or snippet.
     * Returns true for full script, false for snippet.
     */
    private boolean askScriptOrSnippet() {
        System.out.println("\nSelect Mode:\n1. Script (wrapped in Java.perform)\n2. Snippet (hook only)");
        while (true) {
            String line = scanner.nextLine().trim();
            if ("1".equals(line)) return true;
            if ("2".equals(line)) return false;
            System.out.println("Invalid option. Enter 1 or 2:");
        }
    }
}
