package com.amrts.fridahelper.core.model;

/**
 * Immutable representation of a native hook target.
 *
 * Supports two targeting modes:
 * - EXPORT: resolve by name via Module.getExportByName / Module.findExportByName
 * - ADDRESS: hook a raw pointer (e.g. ptr("0xDEAD"))
 *
 * libName is nullable: null means wildcard (resolves across all loaded modules).
 * In the generated JS, null libName produces: Module.findExportByName(null, "func")
 *
 * Use the builder to construct instances — it enforces valid field combinations
 * and avoids boolean explosion for the optional features (waitForLoad, setTimeout).
 *
 * waitForLoad and setTimeoutMs are *metadata* that tell the generator/wrapper
 * how to wrap the hook script. They do NOT affect the core Interceptor.attach body.
 */
public final class NativeSymbol {

    public enum TargetMode { EXPORT, ADDRESS }

    private final String libName;       // nullable: null = wildcard
    private final String exportName;    // required for EXPORT, unused for ADDRESS
    private final String address;       // required for ADDRESS, unused for EXPORT
    private final int argCount;
    private final TargetMode targetMode;
    private final boolean waitForLoad;
    private final int setTimeoutMs;     // 0 = no setTimeout wrapping

    private NativeSymbol(Builder b) {
        this.libName = b.libName;
        this.exportName = b.exportName;
        this.address = b.address;
        this.argCount = b.argCount;
        this.targetMode = b.targetMode;
        this.waitForLoad = b.waitForLoad;
        this.setTimeoutMs = b.setTimeoutMs;
    }

    // --- Getters ---

    /** Library name, or null for wildcard resolution. */
    public String getLibName() { return libName; }

    /** Export/symbol name (EXPORT mode only). */
    public String getExportName() { return exportName; }

    /** Raw address string, e.g. "0x12AB" (ADDRESS mode only). */
    public String getAddress() { return address; }

    public int getArgCount() { return argCount; }
    public TargetMode getTargetMode() { return targetMode; }

    /** Whether to wrap in a waitForLibLoading() stub. */
    public boolean isWaitForLoad() { return waitForLoad; }

    /** setTimeout delay in ms. 0 means no setTimeout wrapping. */
    public int getSetTimeoutMs() { return setTimeoutMs; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("NativeSymbol{mode=").append(targetMode);
        if (libName != null) sb.append(", lib=").append(libName);
        if (targetMode == TargetMode.EXPORT) sb.append(", export=").append(exportName);
        if (targetMode == TargetMode.ADDRESS) sb.append(", addr=").append(address);
        sb.append(", args=").append(argCount);
        if (waitForLoad) sb.append(", waitForLoad");
        if (setTimeoutMs > 0) sb.append(", setTimeout=").append(setTimeoutMs).append("ms");
        return sb.append("}").toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NativeSymbol)) return false;
        NativeSymbol that = (NativeSymbol) o;
        return argCount == that.argCount
                && waitForLoad == that.waitForLoad
                && setTimeoutMs == that.setTimeoutMs
                && targetMode == that.targetMode
                && eq(libName, that.libName)
                && eq(exportName, that.exportName)
                && eq(address, that.address);
    }

    @Override
    public int hashCode() {
        int h = targetMode.hashCode();
        h = 31 * h + (libName != null ? libName.hashCode() : 0);
        h = 31 * h + (exportName != null ? exportName.hashCode() : 0);
        h = 31 * h + (address != null ? address.hashCode() : 0);
        h = 31 * h + argCount;
        h = 31 * h + (waitForLoad ? 1 : 0);
        h = 31 * h + setTimeoutMs;
        return h;
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    // ========== Builder ==========

    /**
     * Builder for NativeSymbol. Enforces valid combinations:
     * - EXPORT mode requires exportName, ADDRESS mode requires address.
     * - waitForLoad only valid with EXPORT mode (needs a symbol name to hook after load).
     * - argCount defaults to 0.
     */
    public static final class Builder {
        private String libName;         // null = wildcard
        private String exportName;
        private String address;
        private int argCount = 0;
        private TargetMode targetMode = TargetMode.EXPORT;
        private boolean waitForLoad = false;
        private int setTimeoutMs = 0;

        public Builder() { }

        /** Set library name. Pass null or empty for wildcard. */
        public Builder libName(String libName) {
            this.libName = (libName == null || libName.trim().isEmpty()) ? null : libName.trim();
            return this;
        }

        /** Set export/symbol name (for EXPORT mode). */
        public Builder exportName(String exportName) {
            this.exportName = exportName;
            this.targetMode = TargetMode.EXPORT;
            return this;
        }

        /** Set raw address (for ADDRESS mode). Automatically switches to ADDRESS mode. */
        public Builder address(String address) {
            this.address = address;
            this.targetMode = TargetMode.ADDRESS;
            return this;
        }

        public Builder argCount(int argCount) {
            this.argCount = argCount;
            return this;
        }

        /** Wrap the hook in a waitForLibLoading() function. */
        public Builder waitForLoad(boolean waitForLoad) {
            this.waitForLoad = waitForLoad;
            return this;
        }

        /** Wrap the hook in setTimeout(function(){ ... }, ms). 0 = no wrapping. */
        public Builder setTimeoutMs(int ms) {
            this.setTimeoutMs = Math.max(0, ms);
            return this;
        }

        public NativeSymbol build() {
            if (targetMode == TargetMode.EXPORT) {
                if (exportName == null || exportName.trim().isEmpty()) {
                    throw new IllegalArgumentException("EXPORT mode requires a non-empty exportName");
                }
            } else {
                if (address == null || address.trim().isEmpty()) {
                    throw new IllegalArgumentException("ADDRESS mode requires a non-empty address");
                }
            }
            if (argCount < 0) {
                throw new IllegalArgumentException("argCount must be >= 0");
            }
            if (waitForLoad && targetMode == TargetMode.ADDRESS) {
                throw new IllegalArgumentException("waitForLoad is not supported with ADDRESS mode");
            }
            if (waitForLoad && libName == null) {
                throw new IllegalArgumentException("waitForLoad requires a library name (cannot be null/wildcard)");
            }
            return new NativeSymbol(this);
        }
    }

    // ========== Convenience factories for backward compatibility ==========

    /**
     * Backward-compatible factory: export-based hook with a required library name.
     * Equivalent to: new Builder().libName(lib).exportName(export).argCount(n).build()
     */
    public static NativeSymbol export(String libName, String exportName, int argCount) {
        return new Builder()
                .libName(libName)
                .exportName(exportName)
                .argCount(argCount)
                .build();
    }
}
