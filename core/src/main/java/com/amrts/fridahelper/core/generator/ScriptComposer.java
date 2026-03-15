package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Composes multiple hook requests into a single Frida script.
 *
 * <h3>Placement strategy</h3>
 * <p>Hooks are classified into two groups based on their structural requirements:
 *
 * <ol>
 *   <li><b>Top-level hooks</b> — Native hooks with {@code waitForLoad} enabled.
 *       These define top-level functions ({@code onLibLoaded}, {@code waitForLibLoading})
 *       and must be emitted outside any wrapper scope. They are placed <em>first</em> in the
 *       output so the dlopen interception is active before anything else runs.</li>
 *   <li><b>Wrapped hooks</b> — All other hooks (Java hooks, standard native hooks).
 *       Their raw bodies are merged and wrapped according to {@link CompositionOptions}.</li>
 * </ol>
 *
 * <h3>Wrapper order (outermost → innermost)</h3>
 * <pre>
 *   setTimeout(function() {           // outermost (if setTimeoutMs &gt; 0)
 *       Java.perform(function() {     // inner (if wrapInPerform)
 *           // merged hook bodies     // innermost
 *       });
 *   }, ms);
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>Not thread-safe. Create one instance per composition session.
 *
 * <h3>Usage</h3>
 * <pre>
 *   ScriptComposer composer = new ScriptComposer();
 *   composer.addRequest(HookRequest.java(method1));
 *   composer.addRequest(HookRequest.java(method2));
 *   composer.addRequest(HookRequest.nativeHook(symbol));
 *
 *   GeneratedScript script = composer.compose(CompositionOptions.withPerform());
 * </pre>
 */
public final class ScriptComposer {

    private static final String INDENT = "    ";

    private final List<HookRequest> requests = new ArrayList<>();
    private final JavaHookGenerator javaGenerator = new JavaHookGenerator();
    private final NativeHookGenerator nativeGenerator = new NativeHookGenerator();

    /** Creates an empty composer. */
    public ScriptComposer() { }

    /**
     * Creates a composer pre-loaded with the given requests (in order).
     * Useful for composing from a snapshot without mutating the original queue.
     *
     * @param initialRequests requests to pre-load; must not contain nulls
     */
    public ScriptComposer(List<HookRequest> initialRequests) {
        if (initialRequests != null) {
            for (HookRequest req : initialRequests) {
                if (req == null) {
                    throw new IllegalArgumentException("HookRequest must not be null");
                }
                requests.add(req);
            }
        }
    }

    /**
     * Adds a hook request to the composition queue.
     * Hooks are composed in insertion order.
     *
     * @param request the hook request to add
     * @throws IllegalArgumentException if request is null
     */
    public void addRequest(HookRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("HookRequest must not be null");
        }
        requests.add(request);
    }

    /**
     * Removes the request at the specified index.
     * Order of remaining items is preserved.
     *
     * @param index zero-based index of the request to remove
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public void removeRequest(int index) {
        requests.remove(index);
    }

    /**
     * Removes all requests from the composition queue.
     */
    public void clear() {
        requests.clear();
    }

    /**
     * Returns the number of requests currently in the queue.
     */
    public int size() {
        return requests.size();
    }

    /**
     * Returns an unmodifiable view of the current request list.
     */
    public List<HookRequest> getRequests() {
        return java.util.Collections.unmodifiableList(requests);
    }

    /**
     * Composes all queued requests into a single Frida script.
     *
     * <p><b>Placement rules:</b>
     * <ol>
     *   <li>Native hooks with {@code waitForLoad} are emitted first, at top-level
     *       (fully generated via {@link NativeHookGenerator#generate(HookRequest)},
     *       including their own setTimeout if set on the symbol).</li>
     *   <li>All other hooks are emitted as raw bodies (via {@code generateBody()}),
     *       merged with blank-line separators, then wrapped per {@code options}.</li>
     * </ol>
     *
     * @param options wrapping options for the composed output
     * @return the composed script; hook type is JAVA if any Java hooks present, else NATIVE
     * @throws IllegalStateException if no requests have been added
     * @throws IllegalArgumentException if options is null
     */
    public GeneratedScript compose(CompositionOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("CompositionOptions must not be null");
        }
        if (requests.isEmpty()) {
            throw new IllegalStateException("No hook requests to compose");
        }

        boolean stackTrace = options.isEnableStackTrace();

        List<String> wrappedBodies = new ArrayList<>();
        boolean hasJava = false;
        boolean hasNative = false;

        LinkedHashMap<String, List<HookRequest>> javaByClass = new LinkedHashMap<>();
        LinkedHashMap<String, List<HookRequest>> waitForLoadByLib = new LinkedHashMap<>();

        // First pass: classify hooks and record insertion order
        // bodyOrder entries: non-null className = Java class group, null = index into nativeBodies
        List<String> bodyOrderKeys = new ArrayList<>();
        List<String> nativeBodies = new ArrayList<>();

        for (HookRequest request : requests) {
            if (isTopLevelHook(request)) {
                String lib = request.getNativeSymbol().getLibName();
                if (!waitForLoadByLib.containsKey(lib)) {
                    waitForLoadByLib.put(lib, new ArrayList<HookRequest>());
                }
                waitForLoadByLib.get(lib).add(request);
                hasNative = true;
            } else if (request.getType() == HookRequest.Type.JAVA) {
                hasJava = true;
                String className = request.getSmaliMethod().getClassName();
                if (!javaByClass.containsKey(className)) {
                    javaByClass.put(className, new ArrayList<HookRequest>());
                    bodyOrderKeys.add(className);
                }
                javaByClass.get(className).add(request);
            } else {
                GeneratedScript body = nativeGenerator.generateBody(request, stackTrace);
                nativeBodies.add(body.getScriptText());
                bodyOrderKeys.add(null);
                hasNative = true;
            }
        }

        // Build wrappedBodies in insertion order, tracking variable names to avoid collisions
        Set<String> usedVarNames = new HashSet<>();
        int nativeIdx = 0;
        for (String key : bodyOrderKeys) {
            if (key != null) {
                wrappedBodies.add(buildJavaClassGroup(key, javaByClass.get(key), stackTrace, usedVarNames));
            } else {
                wrappedBodies.add(nativeBodies.get(nativeIdx++));
            }
        }

        StringBuilder composed = new StringBuilder();

        // Emit helper function definitions when stack trace is enabled
        if (stackTrace) {
            List<String> helpers = new ArrayList<>();
            if (hasJava)   helpers.add(JavaHookGenerator.LOG_FUNCTION);
            if (hasNative)  helpers.add(NativeHookGenerator.NATIVE_LOG_FUNCTION);
            if (!helpers.isEmpty()) {
                composed.append(joinBodies(helpers));
            }
        }

        if (!waitForLoadByLib.isEmpty()) {
            String topLevelSection = buildWaitForLoadSection(waitForLoadByLib, stackTrace);
            if (composed.length() > 0) composed.append("\n\n");
            composed.append(topLevelSection);
        }

        if (!wrappedBodies.isEmpty()) {
            String merged = joinBodies(wrappedBodies);
            String wrapped = applyWrappers(merged, options);

            if (composed.length() > 0) composed.append("\n\n");
            composed.append(wrapped);
        }

        HookRequest.Type resultType = hasJava ? HookRequest.Type.JAVA : HookRequest.Type.NATIVE;
        return new GeneratedScript(composed.toString(), resultType);
    }

    /**
     * Builds a single block for multiple Java hooks targeting the same class.
     * Emits Java.use once, then each method hook using the shared variable.
     * Tracks used variable names to prevent collisions between obfuscated classes.
     */
    private String buildJavaClassGroup(String className, List<HookRequest> hooks,
                                       boolean enableStackTrace, Set<String> usedVarNames) {
        String varName = ensureUnique(JavaHookGenerator.deriveClassVariable(className), usedVarNames);
        usedVarNames.add(varName);
        StringBuilder sb = new StringBuilder();
        sb.append("var ").append(varName).append(" = Java.use(\"").append(className).append("\");\n");

        for (int i = 0; i < hooks.size(); i++) {
            if (i > 0) sb.append("\n\n");
            sb.append(javaGenerator.generateMethodHook(hooks.get(i), varName, enableStackTrace));
        }
        return sb.toString();
    }

    /**
     * Returns a unique variable name by appending a numeric suffix if needed.
     */
    private static String ensureUnique(String name, Set<String> used) {
        if (!used.contains(name)) return name;
        int i = 1;
        while (used.contains(name + i)) i++;
        return name + i;
    }

    /**
     * Builds the entire top-level section for waitForLoad hooks, grouping by library.
     * For each library, one onLibLoaded callback is generated containing all interceptor
     * bodies. The waitForLibLoading helper function is defined once and called per library.
     */
    private String buildWaitForLoadSection(LinkedHashMap<String, List<HookRequest>> byLib,
                                           boolean enableStackTrace) {
        boolean multiLib = byLib.size() > 1;
        StringBuilder sb = new StringBuilder();

        // Compute max setTimeout across all waitForLoad hooks
        int maxTimeout = 0;
        for (List<HookRequest> hooks : byLib.values()) {
            for (HookRequest req : hooks) {
                maxTimeout = Math.max(maxTimeout, req.getNativeSymbol().getSetTimeoutMs());
            }
        }

        // Build onLibLoaded callbacks per library
        List<String> callbackNames = new ArrayList<>();
        List<String> libNames = new ArrayList<>();
        int libIdx = 0;
        for (Map.Entry<String, List<HookRequest>> entry : byLib.entrySet()) {
            String lib = entry.getKey();
            List<HookRequest> hooks = entry.getValue();
            String callbackName = multiLib ? "onLibLoaded_" + libIdx : "onLibLoaded";
            callbackNames.add(callbackName);
            libNames.add(lib);

            if (sb.length() > 0) sb.append("\n\n");
            sb.append("function ").append(callbackName).append("(libName) {\n");
            for (int i = 0; i < hooks.size(); i++) {
                if (i > 0) sb.append("\n");
                NativeSymbol sym = hooks.get(i).getNativeSymbol();
                String varName = hooks.size() == 1 ? "nativeMethod" : "nativeMethod" + i;
                String inner = nativeGenerator.buildWaitForLoadInner(sym, varName, enableStackTrace);
                sb.append(indentBlock(inner, INDENT));
            }
            sb.append("\n}");
            libIdx++;
        }

        // Define waitForLibLoading helper once
        sb.append("\n\n");
        if (multiLib) {
            sb.append("function waitForLibLoading(libraryName, onLoaded) {\n");
        } else {
            sb.append("function waitForLibLoading(libraryName) {\n");
        }
        sb.append("    var isLibLoaded = false;\n\n");
        sb.append("    Interceptor.attach(Module.findExportByName(null, \"android_dlopen_ext\"), {\n");
        sb.append("        onEnter: function(args) {\n");
        sb.append("            var libraryPath = Memory.readCString(args[0]);\n");
        sb.append("            if (libraryPath.includes(libraryName)) {\n");
        sb.append("                console.log(\"[+] Loading library \" + libraryPath + \"...\");\n");
        sb.append("                isLibLoaded = true;\n");
        sb.append("            }\n");
        sb.append("        },\n");
        sb.append("        onLeave: function(retval) {\n");
        sb.append("            if (isLibLoaded) {\n");
        if (multiLib) {
            sb.append("                onLoaded(libraryName);\n");
        } else {
            sb.append("                ").append(callbackNames.get(0)).append("(libraryName);\n");
        }
        sb.append("                isLibLoaded = false;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    });\n");
        sb.append("}\n\n");

        // Emit calls
        for (int i = 0; i < libNames.size(); i++) {
            if (i > 0) sb.append("\n");
            if (multiLib) {
                sb.append("waitForLibLoading(\"").append(libNames.get(i)).append("\", ").append(callbackNames.get(i)).append(");");
            } else {
                sb.append("waitForLibLoading(\"").append(libNames.get(i)).append("\");");
            }
        }

        String section = sb.toString();
        if (maxTimeout > 0) {
            section = wrapInSetTimeout(section, maxTimeout);
        }
        return section;
    }

    /**
     * A hook is "top-level" if it's a native hook with waitForLoad enabled.
     * These define functions at the script root and cannot be nested inside wrappers.
     */
    private boolean isTopLevelHook(HookRequest request) {
        return request.getType() == HookRequest.Type.NATIVE
                && request.getNativeSymbol().isWaitForLoad();
    }

    /**
     * Joins multiple hook bodies with blank-line separators.
     */
    private String joinBodies(List<String> bodies) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bodies.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(bodies.get(i));
        }
        return sb.toString();
    }

    /**
     * Applies composition wrappers in the correct order:
     * innermost: Java.perform, outermost: setTimeout.
     */
    private String applyWrappers(String body, CompositionOptions options) {
        String result = body;

        if (options.isWrapInPerform()) {
            result = wrapInJavaPerform(result);
        }

        if (options.getSetTimeoutMs() > 0) {
            result = wrapInSetTimeout(result, options.getSetTimeoutMs());
        }

        return result;
    }

    private String wrapInJavaPerform(String body) {
        String indented = indentBlock(body, INDENT);
        return "Java.perform(function(){\n" + indented + "\n});";
    }

    private String wrapInSetTimeout(String body, int ms) {
        String indented = indentBlock(body, INDENT);
        return "setTimeout(function() {\n" + indented + "\n}, " + ms + ");";
    }

    private static String indentBlock(String block, String indent) {
        return com.amrts.fridahelper.core.util.ScriptIndent.indentBlock(block, indent);
    }
}
