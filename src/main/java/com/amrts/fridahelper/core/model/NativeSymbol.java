package com.amrts.fridahelper.core.model;

/**
 * Immutable representation of a native library export to hook.
 * Example: libName="libfoo.so", exportName="secret_func", argCount=2
 */
public final class NativeSymbol {

    private final String libName;
    private final String exportName;
    private final int argCount;

    public NativeSymbol(String libName, String exportName, int argCount) {
        if (libName == null || libName.isEmpty()) {
            throw new IllegalArgumentException("libName must not be null or empty");
        }
        if (exportName == null || exportName.isEmpty()) {
            throw new IllegalArgumentException("exportName must not be null or empty");
        }
        if (argCount < 0) {
            throw new IllegalArgumentException("argCount must be >= 0");
        }
        this.libName = libName;
        this.exportName = exportName;
        this.argCount = argCount;
    }

    public String getLibName() { return libName; }
    public String getExportName() { return exportName; }
    public int getArgCount() { return argCount; }

    @Override
    public String toString() {
        return "NativeSymbol{lib=" + libName + ", export=" + exportName + ", args=" + argCount + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NativeSymbol)) return false;
        NativeSymbol that = (NativeSymbol) o;
        return argCount == that.argCount
                && libName.equals(that.libName)
                && exportName.equals(that.exportName);
    }

    @Override
    public int hashCode() {
        int result = libName.hashCode();
        result = 31 * result + exportName.hashCode();
        result = 31 * result + argCount;
        return result;
    }
}
