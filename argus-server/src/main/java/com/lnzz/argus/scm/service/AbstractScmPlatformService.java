package com.lnzz.argus.scm.service;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.config.ScmProperties;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * SCM 平台服务基类
 *
 * @author lnzz
 * @since 1.0.0
 */
public abstract class AbstractScmPlatformService implements ScmPlatformService {

    private final RestTemplate restTemplate = new RestTemplate();
    protected final ScmProperties scmProperties;

    protected AbstractScmPlatformService(ScmProperties scmProperties) {
        this.scmProperties = scmProperties;
    }

    protected ResponseEntity<String> doGet(String url, HttpHeaders headers, Object... uriVars) {
        try {
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class, uriVars);
        } catch (RestClientException e) {
            throw buildScmException("GET", url, e);
        }
    }

    protected ResponseEntity<String> doPost(String url, HttpHeaders headers, Object body, Object... uriVars) {
        try {
            return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class, uriVars);
        } catch (RestClientException e) {
            throw buildScmException("POST", url, e);
        }
    }

    protected ResponseEntity<String> doPut(String url, HttpHeaders headers, Object body, Object... uriVars) {
        try {
            return restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class, uriVars);
        } catch (RestClientException e) {
            throw buildScmException("PUT", url, e);
        }
    }

    protected HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected String encodePath(String filePath) {
        return URLEncoder.encode(filePath, StandardCharsets.UTF_8);
    }

    protected BizException buildScmException(String method, String url, Exception e) {
        return new BizException(ResultCode.SCM_ERROR, method + " " + url + " 调用失败: " + e.getMessage());
    }

    protected boolean matchToken(String expected, String actual) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return expected.equals(actual);
    }

    protected String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    protected boolean isText(String value) {
        return StringUtils.hasText(value);
    }

    protected String jsonBody(Object value) {
        return JSON.toJSONString(value);
    }

    protected abstract String apiBaseUrl(ScmConfig config);

    protected abstract String webBaseUrl(ScmConfig config);

    protected String buildFileRef(ReviewTask task) {
        return isText(task.getLastCommitSha()) ? task.getLastCommitSha() : task.getSourceBranch();
    }
}
