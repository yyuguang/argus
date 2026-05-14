package com.lnzz.argus.gitlab.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.config.GitLabProperties;
import com.lnzz.argus.gitlab.model.DiffFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * GitLab API 客户端
 * <p>封装与 GitLab 的所有 API 交互，其他模块禁止直接调用 GitLab API</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class GitLabApiClient {

    private final GitLabProperties properties;
    private final RestTemplate restTemplate;

    public GitLabApiClient(GitLabProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    // ==================== MR Diff 获取 ====================

    /**
     * M1-06: 获取 MR 的所有变更文件 Diff
     *
     * @param projectId GitLab 项目ID
     * @param mrIid     MR 编号
     * @return 变更文件列表
     */
    public List<DiffFile> getMergeRequestDiffs(Long projectId, Long mrIid) {
        String url = buildUrl("/projects/{projectId}/merge_requests/{mrIid}/diffs");
        try {
            ResponseEntity<String> response = doGet(url, projectId, mrIid);
            JSONArray diffs = JSON.parseArray(response.getBody());
            List<DiffFile> result = new ArrayList<>();
            for (int i = 0; i < diffs.size(); i++) {
                JSONObject diffObj = diffs.getJSONObject(i);
                DiffFile diffFile = new DiffFile();
                diffFile.setOldPath(diffObj.getString("old_path"));
                diffFile.setNewPath(diffObj.getString("new_path"));
                diffFile.setNewFile(diffObj.getBooleanValue("new_file"));
                diffFile.setDeletedFile(diffObj.getBooleanValue("deleted_file"));
                diffFile.setRenamedFile(diffObj.getBooleanValue("renamed_file"));
                diffFile.setDiff(diffObj.getString("diff"));
                result.add(diffFile);
            }
            log.info("获取MR Diff成功, projectId={}, mrIid={}, fileCount={}", projectId, mrIid, result.size());
            return result;
        } catch (RestClientException e) {
            log.error("获取MR Diff失败, projectId={}, mrIid={}", projectId, mrIid, e);
            throw new BizException(ResultCode.SCM_ERROR, "获取MR Diff失败: " + e.getMessage());
        }
    }

    // ==================== 文件内容获取 ====================

    /**
     * M1-07: 获取指定分支/commit 的文件完整内容
     *
     * @param projectId GitLab 项目ID
     * @param filePath  文件路径
     * @param ref       分支名或 commit SHA
     * @return 文件内容
     */
    public String getFileContent(Long projectId, String filePath, String ref) {
        String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
        String url = buildUrl("/projects/{projectId}/repository/files/{filePath}/raw?ref={ref}");
        try {
            ResponseEntity<String> response = doGet(url, projectId, encodedPath, ref);
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("获取文件内容失败, projectId={}, filePath={}, ref={}", projectId, filePath, ref, e);
            return null;
        }
    }

    /**
     * M1-09: 批量获取多个文件内容
     *
     * @param projectId GitLab 项目ID
     * @param filePaths 文件路径列表
     * @param ref       分支名或 commit SHA
     * @return 文件路径 → 文件内容的映射
     */
    public Map<String, String> batchGetFileContent(Long projectId, List<String> filePaths, String ref) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String filePath : filePaths) {
            String content = getFileContent(projectId, filePath, ref);
            if (content != null) {
                result.put(filePath, content);
            }
        }
        return result;
    }

    // ==================== 评审结果回写 ====================

    /**
     * M1-11: 在 MR 上添加评论
     *
     * @param projectId GitLab 项目ID
     * @param mrIid     MR 编号
     * @param body      评论内容（Markdown）
     * @return 评论ID
     */
    public Long addMergeRequestComment(Long projectId, Long mrIid, String body) {
        String url = buildUrl("/projects/{projectId}/merge_requests/{mrIid}/notes");
        Map<String, String> requestBody = Map.of("body", body);
        try {
            ResponseEntity<String> response = doPost(url, requestBody, projectId, mrIid);
            JSONObject result = JSON.parseObject(response.getBody());
            Long noteId = result.getLong("id");
            log.info("添加MR评论成功, projectId={}, mrIid={}, noteId={}", projectId, mrIid, noteId);
            return noteId;
        } catch (RestClientException e) {
            log.error("添加MR评论失败, projectId={}, mrIid={}", projectId, mrIid, e);
            throw new BizException(ResultCode.SCM_ERROR, "添加MR评论失败: " + e.getMessage());
        }
    }

    /**
     * M1-14: 设置 MR 标签
     *
     * @param projectId GitLab 项目ID
     * @param mrIid     MR 编号
     * @param labels    标签列表
     */
    public void setMergeRequestLabels(Long projectId, Long mrIid, List<String> labels) {
        String url = buildUrl("/projects/{projectId}/merge_requests/{mrIid}");
        Map<String, Object> requestBody = Map.of("labels", String.join(",", labels));
        try {
            doPut(url, requestBody, projectId, mrIid);
            log.info("设置MR标签成功, projectId={}, mrIid={}, labels={}", projectId, mrIid, labels);
        } catch (RestClientException e) {
            log.error("设置MR标签失败, projectId={}, mrIid={}", projectId, mrIid, e);
        }
    }

    // ==================== 项目与用户信息 ====================

    /**
     * M1-15: 获取项目信息
     */
    public JSONObject getProjectInfo(Long projectId) {
        String url = buildUrl("/projects/{projectId}");
        ResponseEntity<String> response = doGet(url, projectId);
        return JSON.parseObject(response.getBody());
    }

    /**
     * M1-17: 获取 MR 详情
     */
    public JSONObject getMergeRequestInfo(Long projectId, Long mrIid) {
        String url = buildUrl("/projects/{projectId}/merge_requests/{mrIid}");
        ResponseEntity<String> response = doGet(url, projectId, mrIid);
        return JSON.parseObject(response.getBody());
    }

    // ==================== 内部方法 ====================

    private String buildUrl(String path) {
        return properties.getUrl() + "/api/v4" + path;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", properties.getToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> doGet(String url, Object... uriVars) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class, uriVars);
    }

    private ResponseEntity<String> doPost(String url, Object body, Object... uriVars) {
        HttpEntity<Object> entity = new HttpEntity<>(body, buildHeaders());
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class, uriVars);
    }

    private ResponseEntity<String> doPut(String url, Object body, Object... uriVars) {
        HttpEntity<Object> entity = new HttpEntity<>(body, buildHeaders());
        return restTemplate.exchange(url, HttpMethod.PUT, entity, String.class, uriVars);
    }
}
