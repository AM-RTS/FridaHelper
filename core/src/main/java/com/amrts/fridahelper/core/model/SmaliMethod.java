package com.amrts.fridahelper.core.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Immutable representation of a parsed smali method signature.
 * Example input: Lcom/example/Foo;->bar(ILjava/lang/String;)V
 * Produces: className="com.example.Foo", methodName="bar",
 *           paramTypes=["int","java.lang.String"], returnType="void"
 */
public final class SmaliMethod {

    private final String className;
    private final String methodName;
    private final List<String> paramTypes;
    private final String returnType;

    public SmaliMethod(String className, String methodName, List<String> paramTypes, String returnType) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className must not be null or empty");
        }
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("methodName must not be null or empty");
        }
        this.className = className;
        this.methodName = methodName;
        this.paramTypes = paramTypes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(Arrays.asList(paramTypes.toArray(new String[0])));
        this.returnType = returnType == null ? "" : returnType;
    }

    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public List<String> getParamTypes() { return paramTypes; }
    public String getReturnType() { return returnType; }

    @Override
    public String toString() {
        return "SmaliMethod{class=" + className + ", method=" + methodName
                + ", params=" + paramTypes + ", ret=" + returnType + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SmaliMethod)) return false;
        SmaliMethod that = (SmaliMethod) o;
        return className.equals(that.className)
                && methodName.equals(that.methodName)
                && paramTypes.equals(that.paramTypes)
                && returnType.equals(that.returnType);
    }

    @Override
    public int hashCode() {
        int result = className.hashCode();
        result = 31 * result + methodName.hashCode();
        result = 31 * result + paramTypes.hashCode();
        result = 31 * result + returnType.hashCode();
        return result;
    }
}
