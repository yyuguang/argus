package com.lnzz.argus.error.parse;

import java.io.Serial;
import java.io.Serializable;

/**
 * 栈帧 —— 单行调用帧的结构化表示
 *
 * @author lnzz
 * @since 1.0.0
 */
public class StackFrame implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 全限定类名 */
    private String className;

    /** 方法名 */
    private String methodName;

    /** 文件名 */
    private String fileName;

    /** 行号 */
    private Integer lineNumber;

    /** 是否为 Native 方法 */
    private boolean nativeMethod;

    /** 是否为未知来源 */
    private boolean unknownSource;

    /** 原始帧文本 */
    private String raw;

    public static StackFrame of(String className, String methodName, String fileName,
                                 Integer lineNumber, String raw) {
        StackFrame frame = new StackFrame();
        frame.className = className;
        frame.methodName = methodName;
        frame.fileName = fileName;
        frame.lineNumber = lineNumber;
        frame.raw = raw;
        return frame;
    }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    public boolean isNativeMethod() { return nativeMethod; }
    public void setNativeMethod(boolean nativeMethod) { this.nativeMethod = nativeMethod; }

    public boolean isUnknownSource() { return unknownSource; }
    public void setUnknownSource(boolean unknownSource) { this.unknownSource = unknownSource; }

    public String getRaw() { return raw; }
    public void setRaw(String raw) { this.raw = raw; }

    @Override
    public String toString() {
        return raw != null ? raw : className + "." + methodName
                + "(" + (fileName != null ? fileName : "Unknown Source") + ":"
                + (lineNumber != null ? lineNumber : "") + ")";
    }
}
