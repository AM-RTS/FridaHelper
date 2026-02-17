package com.amrts.fridahelper.cli;

import com.amrts.fridahelper.core.FridaHelperVersion;
import com.amrts.fridahelper.core.generator.CompositionOptions;
import com.amrts.fridahelper.core.generator.JavaHookGenerator;
import com.amrts.fridahelper.core.generator.NativeHookGenerator;
import com.amrts.fridahelper.core.generator.ScriptComposer;
import com.amrts.fridahelper.core.generator.ScriptGenerator;
import com.amrts.fridahelper.core.generator.ScriptWrapper;
import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;
import com.amrts.fridahelper.core.model.SmaliMethod;
import com.amrts.fridahelper.core.parser.SmaliSignatureParser;

import java.util.List;
import java.util.Scanner;

/**
 * Handles CLI menu interactions. All I/O is confined to this class and
 * {@link FridaHelperCli}. The core module is invoked as pure function calls.
 *
 * Supports:
 * - Single Java hook generation (original flow)
 * - Single native hook generation
 * - Multi-hook session: queue multiple hooks, compose into one script
 */
public final class CliMenuHandler {

    private static final String MULTI_HOOK_MENU =
            "\n=== Multi-Hook Session ===\n"
            + "1. Add Java Hook\n"
            + "2. Add Native Hook\n"
            + "3. View Current Queue\n"
            + "4. Remove Hook (by index)\n"
            + "5. Clear Queue\n"
            + "6. Compose Script\n"
            + "7. Exit Session\n";

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

    // ========== Single-hook flows (backward compatible) ==========

    /**
     * Runs the Java hook flow: reads smali signature, asks script/snippet mode, generates output.
     */
    public void handleJavaHook() {
        HookRequest request = collectJavaHookInput();
        if (request == null) return;

        boolean fullScript = askScriptOrSnippet();
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
     * Runs the native hook flow with full option support.
     */
    public void handleNativeHook() {
        HookRequest request = collectNativeHookInput();
        if (request == null) return;

        GeneratedScript result = nativeGenerator.generate(request);

        System.out.println("\n[*] Here's your native frida script! :\n");
        System.out.println(result.getScriptText());
    }

    // ========== Multi-hook session ==========

    /**
     * Runs the multi-hook composition session.
     * Maintains a ScriptComposer instance for the duration of the session.
     */
    public void handleMultiHookSession() {
        ScriptComposer composer = new ScriptComposer();
        boolean inSession = true;

        while (inSession) {
            System.out.println(MULTI_HOOK_MENU);
            System.out.print("Queue: " + composer.size() + " hook(s)\n> ");
            String input = scanner.nextLine().trim();

            switch (input) {
                case "1":
                    addJavaHookToComposer(composer);
                    break;
                case "2":
                    addNativeHookToComposer(composer);
                    break;
                case "3":
                    viewQueue(composer);
                    break;
                case "4":
                    removeHookFromComposer(composer);
                    break;
                case "5":
                    composer.clear();
                    System.out.println("Queue cleared.");
                    break;
                case "6":
                    composeFromComposer(composer);
                    break;
                case "7":
                    System.out.println("Exiting multi-hook session.");
                    inSession = false;
                    break;
                default:
                    System.out.println("Invalid option. Please enter 1-7.");
                    break;
            }
        }
    }

    private void addJavaHookToComposer(ScriptComposer composer) {
        HookRequest request = collectJavaHookInput();
        if (request == null) return;

        composer.addRequest(request);
        System.out.println("Java hook added. Total: " + composer.size());
    }

    private void addNativeHookToComposer(ScriptComposer composer) {
        HookRequest request = collectNativeHookInput();
        if (request == null) return;

        composer.addRequest(request);
        System.out.println("Native hook added. Total: " + composer.size());
    }

    private void viewQueue(ScriptComposer composer) {
        List<HookRequest> requests = composer.getRequests();
        if (requests.isEmpty()) {
            System.out.println("\nQueue is empty.");
            return;
        }

        System.out.println("\n=== Hook Queue (" + requests.size() + ") ===");
        for (int i = 0; i < requests.size(); i++) {
            HookRequest req = requests.get(i);
            System.out.println("  " + (i + 1) + ". " + formatHookSummary(req));
        }
    }

    private void removeHookFromComposer(ScriptComposer composer) {
        if (composer.size() == 0) {
            System.out.println("Queue is empty. Nothing to remove.");
            return;
        }

        viewQueue(composer);
        System.out.print("\nEnter hook number to remove (1-" + composer.size() + "): ");
        String input = scanner.nextLine().trim();

        int index;
        try {
            index = Integer.parseInt(input) - 1;
        } catch (NumberFormatException e) {
            System.out.println("Error: please enter a valid number.");
            return;
        }

        if (index < 0 || index >= composer.size()) {
            System.out.println("Error: index out of bounds. Valid range: 1-" + composer.size());
            return;
        }

        composer.removeRequest(index);
        System.out.println("Hook removed. Remaining: " + composer.size());
    }

    private void composeFromComposer(ScriptComposer composer) {
        if (composer.size() == 0) {
            System.out.println("Queue is empty. Add hooks first.");
            return;
        }

        // Collect composition options
        System.out.print("Wrap in Java.perform? (y/n): ");
        String performChoice = scanner.nextLine().trim().toLowerCase();
        boolean wrapInPerform = "y".equals(performChoice) || "yes".equals(performChoice);

        System.out.print("setTimeout delay in ms (0 for none): ");
        int timeoutMs = 0;
        try {
            timeoutMs = Integer.parseInt(scanner.nextLine().trim());
            if (timeoutMs < 0) timeoutMs = 0;
        } catch (NumberFormatException e) {
            System.out.println("Warning: invalid number, skipping setTimeout.");
        }

        CompositionOptions options = CompositionOptions.builder()
                .wrapInPerform(wrapInPerform)
                .setTimeoutMs(timeoutMs)
                .build();

        try {
            GeneratedScript result = composer.compose(options);
            System.out.println("\n[*] Here's your composed frida script! :\n");
            System.out.println(result.getScriptText());
        } catch (Exception e) {
            System.out.println("Error composing script: " + e.getMessage());
        }
    }

    // ========== Shared input collection (reused by single and multi-hook modes) ==========

    /**
     * Collects Java hook input from the user.
     * Returns a HookRequest, or null if input is invalid.
     */
    private HookRequest collectJavaHookInput() {
        System.out.println("\nInput Your Method's Signature (in smali syntax): ");
        String signature = scanner.nextLine().trim();

        if (signature.isEmpty()) {
            System.out.println("Error: empty signature.");
            return null;
        }

        SmaliMethod method;
        try {
            method = parser.parse(signature);
        } catch (IllegalArgumentException e) {
            System.out.println("\nError!: " + e.getMessage());
            System.out.println("Make sure its in smali syntax. Example: Lcom/example/Foo;->bar(I)V");
            return null;
        }

        return HookRequest.java(method);
    }

    /**
     * Collects native hook input from the user.
     * Returns a HookRequest, or null if input is invalid.
     */
    private HookRequest collectNativeHookInput() {
        System.out.println("\nNative Hook Target:");
        System.out.println("1. Export/symbol name");
        System.out.println("2. Raw address (ptr)");

        String modeChoice = promptLine("> ");
        NativeSymbol.Builder builder = new NativeSymbol.Builder();

        if ("1".equals(modeChoice)) {
            if (!handleExportMode(builder)) return null;
        } else if ("2".equals(modeChoice)) {
            if (!handleAddressMode(builder)) return null;
        } else {
            System.out.println("Invalid option.");
            return null;
        }

        // Arg count
        System.out.println("Enter number of arguments (0 if unknown): ");
        int argCount;
        try {
            argCount = Integer.parseInt(promptLine(""));
            if (argCount < 0) {
                System.out.println("Error: argument count must be >= 0.");
                return null;
            }
        } catch (NumberFormatException e) {
            System.out.println("Error: please enter a valid integer.");
            return null;
        }
        builder.argCount(argCount);

        // setTimeout option
        System.out.println("Wrap in setTimeout? (enter delay in ms, or 0 for none): ");
        try {
            int delay = Integer.parseInt(promptLine(""));
            if (delay > 0) builder.setTimeoutMs(delay);
        } catch (NumberFormatException e) {
            System.out.println("Warning: invalid number, skipping setTimeout.");
        }

        // Build
        NativeSymbol symbol;
        try {
            symbol = builder.build();
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return null;
        }

        return HookRequest.nativeHook(symbol);
    }

    /**
     * Handles export-based native hook input.
     * Returns true on success, false if input is invalid.
     */
    private boolean handleExportMode(NativeSymbol.Builder builder) {
        System.out.println("\nEnter library name (e.g. libfoo.so, or leave empty for null/wildcard): ");
        String libName = promptLine("");
        builder.libName(libName.isEmpty() ? null : libName);

        System.out.println("Enter export/symbol name (e.g. secret_func): ");
        String exportName = promptLine("");
        if (exportName.isEmpty()) {
            System.out.println("Error: export name cannot be empty.");
            return false;
        }
        builder.exportName(exportName);

        // Wait for load option (only if lib is specified)
        if (!libName.isEmpty()) {
            System.out.println("Wait for library to load? (y/n): ");
            String waitChoice = promptLine("").toLowerCase();
            if ("y".equals(waitChoice) || "yes".equals(waitChoice)) {
                builder.waitForLoad(true);
            }
        }

        return true;
    }

    /**
     * Handles address-based native hook input.
     * Returns true on success, false if input is invalid.
     */
    private boolean handleAddressMode(NativeSymbol.Builder builder) {
        System.out.println("\nEnter address (e.g. 0x12AB): ");
        String address = promptLine("");
        if (address.isEmpty()) {
            System.out.println("Error: address cannot be empty.");
            return false;
        }
        builder.address(address);
        return true;
    }

    // ========== Utility ==========

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

    /**
     * Reads a trimmed line from scanner with optional prompt.
     */
    private String promptLine(String prompt) {
        if (!prompt.isEmpty()) System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    /**
     * Formats a concise summary string for a hook request (for queue display).
     */
    private String formatHookSummary(HookRequest request) {
        if (request.getType() == HookRequest.Type.JAVA) {
            SmaliMethod m = request.getSmaliMethod();
            return "[JAVA] " + m.getClassName() + "." + m.getMethodName()
                    + " (" + m.getParamTypes().size() + " params)";
        } else {
            NativeSymbol s = request.getNativeSymbol();
            if (s.getTargetMode() == NativeSymbol.TargetMode.ADDRESS) {
                return "[NATIVE] ptr(" + s.getAddress() + ") (" + s.getArgCount() + " args)"
                        + (s.isWaitForLoad() ? " [waitForLoad]" : "");
            }
            String lib = s.getLibName() != null ? s.getLibName() : "null";
            return "[NATIVE] " + lib + " -> " + s.getExportName()
                    + " (" + s.getArgCount() + " args)"
                    + (s.isWaitForLoad() ? " [waitForLoad]" : "");
        }
    }
}
