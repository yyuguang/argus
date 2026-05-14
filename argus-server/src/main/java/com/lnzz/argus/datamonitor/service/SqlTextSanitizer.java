package com.lnzz.argus.datamonitor.service;

import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * SQL 文本指纹与脱敏工具。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
public class SqlTextSanitizer {

    public String fingerprint(String sqlText) {
        String normalized = normalize(sqlText);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
    }

    public String mask(String sqlText) {
        if (!StringUtils.hasText(sqlText)) {
            return sqlText;
        }
        String masked = sqlText.replaceAll("'([^']*)'", "'?'");
        masked = masked.replaceAll("\"([^\"]*)\"", "\"?\"");
        masked = masked.replaceAll("\\b\\d{6,}\\b", "?");
        return masked;
    }

    private String normalize(String sqlText) {
        if (!StringUtils.hasText(sqlText)) {
            return null;
        }
        String normalized = sqlText.replaceAll("'([^']*)'", "?")
                .replaceAll("\"([^\"]*)\"", "?")
                .replaceAll("\\b\\d+\\b", "?")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}
