package com.amrts.fridahelper.core.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Filters {@link SmaliMethodEntry} instances for batch hook generation.
 *
 * <p>Hard rules (always enforced, not configurable):
 * <ul>
 *   <li>Abstract methods are always excluded (unhookable via Frida)</li>
 *   <li>Synthetic and bridge methods are always excluded (compiler noise)</li>
 * </ul>
 *
 * <p>Soft rules (configurable via Builder):
 * <ul>
 *   <li>Skip constructors ({@code <init>}, {@code <clinit>})</li>
 *   <li>Include/exclude classes by regex</li>
 *   <li>Include/exclude methods by regex</li>
 * </ul>
 *
 * Immutable. Use {@link #builder()} to construct instances.
 */
public final class BatchFilter {

    private final boolean skipConstructors;
    private final Pattern includeClassPattern;
    private final Pattern excludeClassPattern;
    private final Pattern includeMethodPattern;
    private final Pattern excludeMethodPattern;

    private BatchFilter(Builder builder) {
        this.skipConstructors = builder.skipConstructors;
        this.includeClassPattern = builder.includeClassPattern;
        this.excludeClassPattern = builder.excludeClassPattern;
        this.includeMethodPattern = builder.includeMethodPattern;
        this.excludeMethodPattern = builder.excludeMethodPattern;
    }

    /**
     * Tests whether a method entry passes all filter criteria.
     */
    public boolean accepts(SmaliMethodEntry entry) {
        if (entry == null) return false;

        // Hard rules — always enforced
        if (entry.isAbstract()) return false;
        if (entry.isSynthetic() || entry.isBridge()) return false;

        // Soft rules — configurable
        if (skipConstructors && entry.isConstructor()) return false;

        String className = entry.getClassName();
        if (includeClassPattern != null && !includeClassPattern.matcher(className).matches()) {
            return false;
        }
        if (excludeClassPattern != null && excludeClassPattern.matcher(className).matches()) {
            return false;
        }

        String methodName = entry.getMethodName();
        if (includeMethodPattern != null && !includeMethodPattern.matcher(methodName).matches()) {
            return false;
        }
        if (excludeMethodPattern != null && excludeMethodPattern.matcher(methodName).matches()) {
            return false;
        }

        return true;
    }

    /**
     * Filters a list of entries, returning only those that pass all criteria.
     */
    public List<SmaliMethodEntry> apply(List<SmaliMethodEntry> entries) {
        List<SmaliMethodEntry> result = new ArrayList<>();
        for (SmaliMethodEntry entry : entries) {
            if (accepts(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Returns a filter that accepts all hookable methods (hard rules only). */
    public static BatchFilter acceptAll() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean skipConstructors = false;
        private Pattern includeClassPattern;
        private Pattern excludeClassPattern;
        private Pattern includeMethodPattern;
        private Pattern excludeMethodPattern;

        /** Skip {@code <init>} and {@code <clinit>} methods. */
        public Builder skipConstructors(boolean skip) {
            this.skipConstructors = skip;
            return this;
        }

        /** Only include classes whose fully-qualified name matches this regex. */
        public Builder includeClasses(String regex) {
            this.includeClassPattern = regex == null ? null : Pattern.compile(regex);
            return this;
        }

        /** Exclude classes whose fully-qualified name matches this regex. */
        public Builder excludeClasses(String regex) {
            this.excludeClassPattern = regex == null ? null : Pattern.compile(regex);
            return this;
        }

        /** Only include methods whose name matches this regex. */
        public Builder includeMethods(String regex) {
            this.includeMethodPattern = regex == null ? null : Pattern.compile(regex);
            return this;
        }

        /** Exclude methods whose name matches this regex. */
        public Builder excludeMethods(String regex) {
            this.excludeMethodPattern = regex == null ? null : Pattern.compile(regex);
            return this;
        }

        public BatchFilter build() {
            return new BatchFilter(this);
        }
    }
}
