package com.amrts.fridahelper.core.generator;

import com.amrts.fridahelper.core.model.GeneratedScript;
import com.amrts.fridahelper.core.model.HookRequest;

/**
 * Core abstraction for Frida script generation.
 *
 * Design rationale:
 * - No hookType() method: the hook type is carried by HookRequest.Type, not the generator.
 *   Adding it here would duplicate identity and force implementors to hard-code a string.
 * - No wrapInPerform parameter: wrapping (Java.perform, setImmediate, etc.) is a
 *   presentation/caller concern handled by {@link ScriptWrapper}. Generators produce
 *   the minimal correct hook body.
 *
 * To add a new hook type: implement this interface, create a new HookRequest.Type variant.
 */
public interface ScriptGenerator {

    /**
     * Generates a Frida hook script from the given request.
     *
     * @param request the hook request (Java or Native)
     * @return generated script with metadata
     * @throws IllegalArgumentException if the request type is unsupported by this generator
     */
    GeneratedScript generate(HookRequest request);
}
