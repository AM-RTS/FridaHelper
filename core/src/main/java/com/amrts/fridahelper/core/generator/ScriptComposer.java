package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;

import java.util.ArrayList;
import java.util.List;

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

        List<String> topLevelScripts = new ArrayList<>();
        List<String> wrappedBodies = new ArrayList<>();
        boolean hasJava = false;

        for (HookRequest request : requests) {
            if (isTopLevelHook(request)) {
                // waitForLoad hooks are emitted fully generated (with their own wrappers)
                GeneratedScript full = nativeGenerator.generate(request);
                topLevelScripts.add(full.getScriptText());
            } else {
                // All other hooks contribute their raw body to the wrapped section
                GeneratedScript body = generateBodyFor(request);
                wrappedBodies.add(body.getScriptText());
                if (request.getType() == HookRequest.Type.JAVA) {
                    hasJava = true;
                }
            }
        }

        StringBuilder composed = new StringBuilder();

        // 1. Top-level hooks first (waitForLoad)
        for (String topLevel : topLevelScripts) {
            if (composed.length() > 0) {
                composed.append("\n\n");
            }
            composed.append(topLevel);
        }

        // 2. Wrapped hooks section
        if (!wrappedBodies.isEmpty()) {
            String merged = joinBodies(wrappedBodies);
            String wrapped = applyWrappers(merged, options);

            if (composed.length() > 0) {
                composed.append("\n\n");
            }
            composed.append(wrapped);
        }

        HookRequest.Type resultType = hasJava ? HookRequest.Type.JAVA : HookRequest.Type.NATIVE;
        return new GeneratedScript(composed.toString(), resultType);
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
     * Dispatches to the correct generator's generateBody() based on request type.
     */
    private GeneratedScript generateBodyFor(HookRequest request) {
        if (request.getType() == HookRequest.Type.JAVA) {
            return javaGenerator.generateBody(request);
        } else {
            return nativeGenerator.generateBody(request);
        }
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

    /**
     * Indents every non-empty line of a multi-line string by the given prefix.
     */
    private static String indentBlock(String block, String indent) {
        String[] lines = block.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append("\n");
            if (!lines[i].isEmpty()) {
                sb.append(indent).append(lines[i]);
            }
        }
        return sb.toString();
    }
}
