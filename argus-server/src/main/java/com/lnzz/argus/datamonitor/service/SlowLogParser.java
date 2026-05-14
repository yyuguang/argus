package com.lnzz.argus.datamonitor.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MySQL 5.7 slow log 片段解析器。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
public class SlowLogParser {

    private static final Pattern QUERY_TIME_PATTERN = Pattern.compile("Query_time:\\s*([0-9.]+)");
    private static final Pattern LOCK_TIME_PATTERN = Pattern.compile("Lock_time:\\s*([0-9.]+)");
    private static final Pattern ROWS_SENT_PATTERN = Pattern.compile("Rows_sent:\\s*(\\d+)");
    private static final Pattern ROWS_EXAMINED_PATTERN = Pattern.compile("Rows_examined:\\s*(\\d+)");
    private static final DateTimeFormatter MYSQL57_TIME = DateTimeFormatter.ofPattern("yyMMdd HH:mm:ss");

    public ParsedSlowLog parse(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("slow log 内容不能为空");
        }
        String[] lines = content.split("\\r?\\n");
        LocalDateTime occurredAt = null;
        Long queryTimeMs = null;
        Long lockTimeMs = null;
        Long rowsSent = null;
        Long rowsExamined = null;
        StringBuilder sql = new StringBuilder();
        boolean sqlStarted = false;
        for (String line : lines) {
            if (line.startsWith("# Time:")) {
                occurredAt = parseTime(line.substring("# Time:".length()).trim());
                continue;
            }
            if (line.startsWith("# Query_time:")) {
                queryTimeMs = secondsToMs(matchDecimal(QUERY_TIME_PATTERN, line));
                lockTimeMs = secondsToMs(matchDecimal(LOCK_TIME_PATTERN, line));
                rowsSent = matchLong(ROWS_SENT_PATTERN, line);
                rowsExamined = matchLong(ROWS_EXAMINED_PATTERN, line);
                continue;
            }
            if (line.startsWith("SET timestamp=")) {
                sqlStarted = true;
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            if (sqlStarted || StringUtils.hasText(line)) {
                sqlStarted = true;
                sql.append(line).append('\n');
            }
        }
        String sqlText = sql.toString().trim();
        if (sqlText.endsWith(";")) {
            sqlText = sqlText.substring(0, sqlText.length() - 1).trim();
        }
        return new ParsedSlowLog(queryTimeMs, lockTimeMs, rowsSent, rowsExamined, sqlText, occurredAt);
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, MYSQL57_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double matchDecimal(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

    private Long matchLong(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private Long secondsToMs(Double seconds) {
        return seconds == null ? null : Math.round(seconds * 1000);
    }

    public record ParsedSlowLog(
            Long queryTimeMs,
            Long lockTimeMs,
            Long rowsSent,
            Long rowsExamined,
            String sqlText,
            LocalDateTime occurredAt
    ) {
    }
}
