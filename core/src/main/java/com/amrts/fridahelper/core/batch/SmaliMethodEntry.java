package com.amrts.fridahelper.core.batch;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A method entry extracted from a .smali file.
 * Carries enough metadata for filtering before full parsing/generation.
 */
public final class SmaliMethodEntry {

    private final String classDescriptor;
    private final String className;
    private final String methodName;
    private final String fullSignature;
    private final Set<String> accessFlags;

    public SmaliMethodEntry(String classDescriptor, String methodName,
                            String fullSignature, Set<String> accessFlags) {
        this.classDescriptor = classDescriptor;
        this.className = descriptorToClassName(classDescriptor);
        this.methodName = methodName;
        this.fullSignature = fullSignature;
        this.accessFlags = accessFlags == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new HashSet<>(accessFlags));
    }

    public String getClassDescriptor() { return classDescriptor; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getFullSignature() { return fullSignature; }
    public Set<String> getAccessFlags() { return accessFlags; }

    public boolean isAbstract() { return accessFlags.contains("abstract"); }
    public boolean isNative() { return accessFlags.contains("native"); }
    public boolean isSynthetic() { return accessFlags.contains("synthetic"); }
    public boolean isBridge() { return accessFlags.contains("bridge"); }

    public boolean isConstructor() {
        return "<init>".equals(methodName) || "<clinit>".equals(methodName);
    }

    @Override
    public String toString() {
        return "SmaliMethodEntry{" + fullSignature + ", flags=" + accessFlags + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SmaliMethodEntry)) return false;
        SmaliMethodEntry that = (SmaliMethodEntry) o;
        return fullSignature.equals(that.fullSignature);
    }

    @Override
    public int hashCode() {
        return fullSignature.hashCode();
    }

    private static String descriptorToClassName(String descriptor) {
        if (descriptor == null || descriptor.length() < 3) return "";
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }
}
