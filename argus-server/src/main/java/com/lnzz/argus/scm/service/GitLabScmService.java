package com.lnzz.argus.scm.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lnzz.argus.config.ScmProperties;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.model.PullRequestEvent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GitLab 平台服务
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
public class GitLabScmService extends AbstractScmPlatformService {

    public GitLabScmService(ScmProperties scmProperties) {
        super(scmProperties);
    }

    @Override
    public String getProvider() {
        return "gitlab";
    }

    @Override
    public PullRequestEvent parseWebhookEvent(Map<String, String> headers, String payload) {
        JSONObject json = JSONObject.parseObject(payload);
        if (!"merge_request".equals(json.getString("object_kind"))) {
            return null;
        }

        JSONObject project = json.getJSONObject("project");
        JSONObject attrs = json.getJSONObject("object_attributes");
        JSONObject user = json.getJSONObject("user");
        JSONObject lastCommit = attrs.getJSONObject("last_commit");

        PullRequestEvent event = new PullRequestEvent();
        event.setScmProvider(getProvider());
        event.setEventType(attrs.getString("action"));
        event.setProjectId(project.getLong("id"));
        event.setProjectName(project.getString("name"));
        event.setProjectUrl(project.getString("web_url"));
        event.setRepoName(project.getString("path"));
        event.setRepoOwner(project.getString("path_with_namespace") != null
                ? project.getString("path_with_namespace").replace("/" + project.getString("path"), "")
                : null);
        event.setMrIid(attrs.getLong("iid"));
        event.setMrTitle(attrs.getString("title"));
        event.setMrUrl(attrs.getString("url"));
        event.setMrState(attrs.getString("state"));
        event.setAuthorId(user.getString("id"));
        event.setAuthorName(user.getString("name"));
        event.setSourceBranch(attrs.getString("source_branch"));
        event.setTargetBranch(attrs.getString("target_branch"));
        if (lastCommit != null) {
            event.setLastCommitSha(lastCommit.getString("id"));
        }
        return event;
    }

    @Override
    public boolean verifyWebhookSignature(ScmConfig config, Map<String, String> headers, String payload) {
        return matchToken(config.getWebhookSecret(), headers.getOrDefault("x-gitlab-token", ""));
    }

    @Override
    public List<DiffFile> getPullRequestDiffs(ScmConfig config, ReviewTask task) {
        String url = apiBaseUrl(config) + "/projects/{projectId}/merge_requests/{mrIid}/diffs";
        ResponseEntity<String> response = doGet(url, buildHeaders(config), task.getProjectId(), task.getMrIid());
        JSONArray diffs = JSON.parseArray(response.getBody());
        List<DiffFile> result = new ArrayList<>();
        for (int i = 0; i < diffs.size(); i++) {
            JSONObject item = diffs.getJSONObject(i);
            DiffFile diffFile = new DiffFile();
            diffFile.setOldPath(item.getString("old_path"));
            diffFile.setNewPath(item.getString("new_path"));
            diffFile.setNewFile(item.getBooleanValue("new_file"));
            diffFile.setDeletedFile(item.getBooleanValue("deleted_file"));
            diffFile.setRenamedFile(item.getBooleanValue("renamed_file"));
            diffFile.setDiff(item.getString("diff"));
            result.add(diffFile);
        }
        return result;
    }

    @Override
    public String getFileContent(ScmConfig config, ReviewTask task, String filePath, String ref) {
        String url = apiBaseUrl(config) + "/projects/{projectId}/repository/files/{filePath}/raw?ref={ref}";
        return doGet(url, buildHeaders(config), task.getProjectId(), encodePath(filePath), ref).getBody();
    }

    @Override
    public Long addPullRequestComment(ScmConfig config, ReviewTask task, String body) {
        String url = apiBaseUrl(config) + "/projects/{projectId}/merge_requests/{mrIid}/notes";
        ResponseEntity<String> response = doPost(url, buildHeaders(config), Map.of("body", body),
                task.getProjectId(), task.getMrIid());
        return JSONObject.parseObject(response.getBody()).getLong("id");
    }

    @Override
    public void setPullRequestLabels(ScmConfig config, ReviewTask task, List<String> labels) {
        String url = apiBaseUrl(config) + "/projects/{projectId}/merge_requests/{mrIid}";
        doPut(url, buildHeaders(config), Map.of("labels", String.join(",", labels)),
                task.getProjectId(), task.getMrIid());
    }

    private HttpHeaders buildHeaders(ScmConfig config) {
        HttpHeaders headers = jsonHeaders();
        headers.set("PRIVATE-TOKEN", config.getAccessToken());
        return headers;
    }

    @Override
    protected String apiBaseUrl(ScmConfig config) {
        return defaultIfBlank(config.getApiBaseUrl(), scmProperties.getGitlab().getApiBaseUrl());
    }

    @Override
    protected String webBaseUrl(ScmConfig config) {
        return defaultIfBlank(config.getWebBaseUrl(), scmProperties.getGitlab().getWebBaseUrl());
    }
}
