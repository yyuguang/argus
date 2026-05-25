package com.lnzz.argus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Argus 安全响应写入器。
 * <p>Spring Security 过滤器链发生认证或授权失败时，统一写回 Argus Result 结构。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class ArgusSecurityResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * 写入后台安全失败响应。
     *
     * @param response   HTTP 响应
     * @param resultCode 业务错误码
     * @throws IOException 响应写入异常
     */
    public void write(HttpServletResponse response, ResultCode resultCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(resultCode));
    }
}
