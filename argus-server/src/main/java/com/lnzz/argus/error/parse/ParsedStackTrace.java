package com.lnzz.argus.error.parse;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析后的异常栈
 *
 * @author lnzz
 * @since 1.0.0
 */
public class ParsedStackTrace implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主异常类名（栈顶异常） */
    private String primaryExceptionClass;

    /** 主异常消息 */
    private String primaryExceptionMessage;

    /** 根因异常类名（最内层 Caused by） */
    private String rootCauseClass;

    /** 根因异常消息 */
    private String rootCauseMessage;

    /** 异常链（从外到内） */
    private List<String> exceptionChain = new ArrayList<>();

    /** 栈顶帧（最靠近异常抛出点） */
    private StackFrame topFrame;

    /** 所有调用帧（去重，最多保留前 20 帧） */
    private List<StackFrame> frames = new ArrayList<>();

    /** 是否解析到完整的异常栈 */
    private boolean parsed;

    /** 解析失败原因 */
    private String parseError;

    public String getPrimaryExceptionClass() { return primaryExceptionClass; }
    public void setPrimaryExceptionClass(String primaryExceptionClass) { this.primaryExceptionClass = primaryExceptionClass; }

    public String getPrimaryExceptionMessage() { return primaryExceptionMessage; }
    public void setPrimaryExceptionMessage(String primaryExceptionMessage) { this.primaryExceptionMessage = primaryExceptionMessage; }

    public String getRootCauseClass() { return rootCauseClass; }
    public void setRootCauseClass(String rootCauseClass) { this.rootCauseClass = rootCauseClass; }

    public String getRootCauseMessage() { return rootCauseMessage; }
    public void setRootCauseMessage(String rootCauseMessage) { this.rootCauseMessage = rootCauseMessage; }

    public List<String> getExceptionChain() { return exceptionChain; }
    public void setExceptionChain(List<String> exceptionChain) { this.exceptionChain = exceptionChain; }

    public StackFrame getTopFrame() { return topFrame; }
    public void setTopFrame(StackFrame topFrame) { this.topFrame = topFrame; }

    public List<StackFrame> getFrames() { return frames; }
    public void setFrames(List<StackFrame> frames) { this.frames = frames; }

    public boolean isParsed() { return parsed; }
    public void setParsed(boolean parsed) { this.parsed = parsed; }

    public String getParseError() { return parseError; }
    public void setParseError(String parseError) { this.parseError = parseError; }
}
