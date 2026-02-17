package com.amrts.fridahelper.core.parser;

import com.amrts.fridahelper.core.model.SmaliMethod;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses smali-format method signatures into {@link SmaliMethod} instances.
 * Pure function — no I/O, no mutable state.
 *
 * Expected input format:  Lcom/example/Cls;->methodName(paramDescriptors)returnDescriptor
 * Example:               Lcom/example/Foo;->bar(ILjava/lang/String;)V
 */
public final class SmaliSignatureParser {

    private static final Pattern SIGNATURE_PATTERN =
            Pattern.compile("L(.*?);->(.*?)\\((.*)\\)(.+)");

    private final ParamTypeResolver typeResolver;

    public SmaliSignatureParser() {
        this.typeResolver = new ParamTypeResolver();
    }

    /**
     * Parses a smali method signature string into an immutable SmaliMethod.
     *
     * @param smaliSignature full smali signature, e.g. "Lcom/example/Foo;->bar(I)V"
     * @return parsed SmaliMethod
     * @throws IllegalArgumentException if the signature cannot be parsed
     */
    public SmaliMethod parse(String smaliSignature) {
        if (smaliSignature == null || smaliSignature.trim().isEmpty()) {
            throw new IllegalArgumentException("Signature must not be null or empty");
        }

        Matcher matcher = SIGNATURE_PATTERN.matcher(smaliSignature.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "Invalid smali signature format. Expected: Lclass/path;->method(params)retType — got: "
                            + smaliSignature);
        }

        String rawClass = matcher.group(1).replace("/", ".");
        String methodName = matcher.group(2);
        String rawParams = matcher.group(3);
        String rawReturn = matcher.group(4);

        List<String> paramTypes = (rawParams == null || rawParams.isEmpty())
                ? Collections.emptyList()
                : typeResolver.resolveAll(rawParams);

        String returnType = typeResolver.resolveSingle(rawReturn);

        return new SmaliMethod(rawClass, methodName, paramTypes, returnType);
    }
}
