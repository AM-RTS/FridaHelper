package com.amrts.fridahelper.core.generator;

/**
 * Options controlling how {@link ScriptComposer} wraps and composes multiple hook bodies.
 *
 * <p>These options apply to the <em>final composed output</em>, not to individual hooks.
 * Individual hook wrappers (e.g., native waitForLoad) are handled per-hook by the composer's
 * placement strategy.
 *
 * <p><b>Wrapper order (outermost → innermost):</b>
 * <ol>
 *   <li>{@code setTimeout} (timing, outermost)</li>
 *   <li>{@code Java.perform} (execution context, inner)</li>
 *   <li>Hook bodies (innermost)</li>
 * </ol>
 *
 * Immutable. Use the builder to construct instances.
 */
public final class CompositionOptions {

    private final boolean wrapInPerform;
    private final int setTimeoutMs;

    private CompositionOptions(boolean wrapInPerform, int setTimeoutMs) {
        this.wrapInPerform = wrapInPerform;
        this.setTimeoutMs = Math.max(0, setTimeoutMs);
    }

    /**
     * Whether to wrap the composed body in {@code Java.perform(function(){ ... })}.
     * Required for any Java hooks. Optional for native hooks (needed for JNI functions).
     */
    public boolean isWrapInPerform() {
        return wrapInPerform;
    }

    /**
     * Delay in ms for wrapping the entire composed script in {@code setTimeout(function(){ ... }, ms)}.
     * 0 means no setTimeout wrapping.
     */
    public int getSetTimeoutMs() {
        return setTimeoutMs;
    }

    @Override
    public String toString() {
        return "CompositionOptions{wrapInPerform=" + wrapInPerform
                + ", setTimeoutMs=" + setTimeoutMs + "}";
    }

    // ========== Builder ==========

    public static Builder builder() {
        return new Builder();
    }

    /** Convenience: compose with Java.perform wrapper and no setTimeout. */
    public static CompositionOptions withPerform() {
        return new Builder().wrapInPerform(true).build();
    }

    /** Convenience: compose with no wrappers at all. */
    public static CompositionOptions none() {
        return new Builder().build();
    }

    public static final class Builder {
        private boolean wrapInPerform = false;
        private int setTimeoutMs = 0;

        public Builder wrapInPerform(boolean wrapInPerform) {
            this.wrapInPerform = wrapInPerform;
            return this;
        }

        public Builder setTimeoutMs(int ms) {
            this.setTimeoutMs = ms;
            return this;
        }

        public CompositionOptions build() {
            return new CompositionOptions(wrapInPerform, setTimeoutMs);
        }
    }
}
