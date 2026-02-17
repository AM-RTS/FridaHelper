package com.amrts.fridahelper.core.model;

/**
 * Tagged union for hook requests. Each variant carries its own payload.
 *
 * Design note: Java 8 lacks sealed classes, so we use a final class with
 * a type discriminator + payload fields. Only one payload is non-null per instance.
 * This avoids the visitor/double-dispatch complexity for just two variants.
 * When migrating to Java 17+, convert to a sealed interface with records.
 */
public final class HookRequest {

    public enum Type { JAVA, NATIVE }

    private final Type type;
    private final SmaliMethod smaliMethod;
    private final NativeSymbol nativeSymbol;

    private HookRequest(Type type, SmaliMethod smaliMethod, NativeSymbol nativeSymbol) {
        this.type = type;
        this.smaliMethod = smaliMethod;
        this.nativeSymbol = nativeSymbol;
    }

    /** Factory for Java hook requests. */
    public static HookRequest java(SmaliMethod method) {
        if (method == null) throw new IllegalArgumentException("SmaliMethod must not be null");
        return new HookRequest(Type.JAVA, method, null);
    }

    /** Factory for native hook requests. */
    public static HookRequest nativeHook(NativeSymbol symbol) {
        if (symbol == null) throw new IllegalArgumentException("NativeSymbol must not be null");
        return new HookRequest(Type.NATIVE, null, symbol);
    }

    public Type getType() { return type; }

    public SmaliMethod getSmaliMethod() {
        if (type != Type.JAVA) throw new IllegalStateException("Not a Java hook request");
        return smaliMethod;
    }

    public NativeSymbol getNativeSymbol() {
        if (type != Type.NATIVE) throw new IllegalStateException("Not a Native hook request");
        return nativeSymbol;
    }

    @Override
    public String toString() {
        return type == Type.JAVA
                ? "HookRequest{JAVA, " + smaliMethod + "}"
                : "HookRequest{NATIVE, " + nativeSymbol + "}";
    }
}
