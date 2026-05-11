package com.lnzz.argus.error.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 异常栈解析器（M4-B02）
 * <p>解析原始异常栈文本，提取主异常、根因、异常链、关键调用帧、文件位置</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class StackTraceParser {

    // 异常头行: java.lang.NullPointerException: some message
    private static final Pattern EXCEPTION_HEAD = Pattern.compile(
            "^([\\w.$]+(?:Exception|Error|Throwable))(?::\\s*(.*))?$");

    // Caused by 行: Caused by: java.lang.IllegalStateException: msg
    private static final Pattern CAUSED_BY = Pattern.compile(
            "^Caused by:\\s*([\\w.$]+(?:Exception|Error|Throwable))(?::\\s*(.*))?$");

    // 栈帧行: at com.example.Service.method(File.java:123)
    // 也兼容: at com.example.Service.method(Native Method)
    // 也兼容: at com.example.Service.method(Unknown Source)
    private static final Pattern STACK_FRAME = Pattern.compile(
            "^\\s+at\\s+([\\w.$]+)\\.([\\w<>$]+)\\(([^)]*)\\)");

    // 文件行号: File.java:123
    private static final Pattern FILE_LINE = Pattern.compile(
            "^([\\w.$]+\\.\\w+):(\\d+)$");

    // 更多省略行: ... 23 more
    private static final Pattern MORE_FRAMES = Pattern.compile(
            "^\\s+\\.\\.\\.\\s+\\d+\\s+(more|common frames omitted)\\s*$");

    private static final int MAX_FRAMES = 20;

    /**
     * 解析异常栈文本
     *
     * @param rawStackTrace 原始异常栈文本
     * @return 解析结果，如果输入为空或无法解析则返回未解析标记
     */
    public ParsedStackTrace parse(String rawStackTrace) {
        ParsedStackTrace result = new ParsedStackTrace();

        if (rawStackTrace == null || rawStackTrace.isBlank()) {
            result.setParsed(false);
            result.setParseError("异常栈为空");
            return result;
        }

        String[] lines = rawStackTrace.split("\\r?\\n");
        List<String> exceptionChain = new ArrayList<>();
        List<StackFrame> allFrames = new ArrayList<>();
        StackFrame topFrame = null;
        String primaryClass = null;
        String primaryMessage = null;
        String currentCauseClass = null;
        String currentCauseMessage = null;

        boolean inSuppressed = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // 跳过省略行
            if (MORE_FRAMES.matcher(line).matches()) {
                continue;
            }

            // 跳过 suppressed 块
            if (trimmed.startsWith("Suppressed: ")) {
                inSuppressed = true;
                continue;
            }
            if (inSuppressed && (trimmed.startsWith("Caused by:") || trimmed.startsWith("at "))) {
                inSuppressed = false;
            }

            // Caused by 行
            Matcher causedMatcher = CAUSED_BY.matcher(trimmed);
            if (causedMatcher.matches()) {
                String causedClass = causedMatcher.group(1);
                String causedMsg = causedMatcher.group(2);
                exceptionChain.add(causedClass);
                currentCauseClass = causedClass;
                currentCauseMessage = causedMsg;
                continue;
            }

            // 异常头行（第一行）
            if (primaryClass == null) {
                Matcher headMatcher = EXCEPTION_HEAD.matcher(trimmed);
                if (headMatcher.matches()) {
                    primaryClass = headMatcher.group(1);
                    primaryMessage = headMatcher.group(2);
                    exceptionChain.add(primaryClass);
                    currentCauseClass = primaryClass;
                    currentCauseMessage = primaryMessage;
                    continue;
                }
            }

            // 栈帧行
            Matcher frameMatcher = STACK_FRAME.matcher(line);
            if (frameMatcher.matches()) {
                String className = frameMatcher.group(1);
                String methodName = frameMatcher.group(2);
                String location = frameMatcher.group(3);

                String fileName = null;
                Integer lineNumber = null;
                boolean nativeMethod = false;
                boolean unknownSource = false;

                if ("Native Method".equals(location)) {
                    nativeMethod = true;
                } else if ("Unknown Source".equals(location)) {
                    unknownSource = true;
                } else {
                    Matcher fileLineMatcher = FILE_LINE.matcher(location);
                    if (fileLineMatcher.matches()) {
                        fileName = fileLineMatcher.group(1);
                        try {
                            lineNumber = Integer.parseInt(fileLineMatcher.group(2));
                        } catch (NumberFormatException ignored) {
                        }
                    } else {
                        fileName = location;
                    }
                }

                StackFrame frame = StackFrame.of(className, methodName, fileName, lineNumber, line.trim());
                frame.setNativeMethod(nativeMethod);
                frame.setUnknownSource(unknownSource);

                allFrames.add(frame);

                // 取当前异常链的第一个 at 帧作为栈顶帧
                if (topFrame == null) {
                    topFrame = frame;
                }
                continue;
            }

            // Tab 开头可能是延续行（消息多行）
            if (line.startsWith("\t") || trimmed.isEmpty()) {
                continue;
            }

            // 更多省略标记
            if (trimmed.matches("\\.\\.\\.\\s+\\d+\\s+more")) {
                continue;
            }
        }

        // 填充结果
        result.setPrimaryExceptionClass(primaryClass);
        result.setPrimaryExceptionMessage(primaryMessage);

        // 根因 = 异常链最末（最深层的 Caused by）
        if (!exceptionChain.isEmpty()) {
            result.setRootCauseClass(exceptionChain.get(exceptionChain.size() - 1));
        }
        result.setRootCauseMessage(currentCauseMessage);

        result.setExceptionChain(exceptionChain);
        result.setTopFrame(topFrame);

        // 截取前 N 帧
        List<StackFrame> limitedFrames = allFrames.size() > MAX_FRAMES
                ? allFrames.subList(0, MAX_FRAMES) : allFrames;
        result.setFrames(limitedFrames);

        result.setParsed(primaryClass != null || !allFrames.isEmpty());

        if (!result.isParsed()) {
            result.setParseError("未能识别异常头或调用帧");
        }

        log.debug("异常栈解析完成: primaryClass={}, rootCause={}, frames={}",
                primaryClass, result.getRootCauseClass(), limitedFrames.size());
        return result;
    }
}
