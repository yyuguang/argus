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
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gitee 平台服务
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
public class GiteeScmService extends AbstractScmPlatformService {

    public GiteeScmService(ScmProperties scmProperties) {
        super(scmProperties);
    }

    @Override
    public String getProvider() {
        return "gitee";
    }

    @Override
    public PullRequestEvent parseWebhookEvent(Map<String, String> headers, String payload) {
        String eventType = headers.getOrDefault("x-gitee-event", "");
        if (!eventType.toLowerCase().contains("pull request")) {
            return null;
        }

        JSONObject json = JSONObject.parseObject(payload);
        JSONObject repository = json.getJSONObject("repository");
        JSONObject pr = json.getJSONObject("pull_request");
        JSONObject author = pr != null ? pr.getJSONObject("author") : null;
        JSONObject head = pr != null ? pr.getJSONObject("head") : null;
        JSONObject base = pr != null ? pr.getJSONObject("base") : null;

        PullRequestEvent event = new PullRequestEvent();
        event.setScmProvider(getProvider());
        event.setEventType(json.getString("action"));
        event.setProjectId(repository.getLong("id"));
        event.setProjectName(repository.getString("full_name"));
        event.setProjectUrl(repository.getString("html_url"));
        event.setRepoOwner(repository.getJSONObject("namespace").getString("path"));
        event.setRepoName(repository.getString("path"));
        event.setMrIid(pr.getLong("number"));
        event.setMrTitle(pr.getString("title"));
        event.setMrUrl(pr.getString("html_url"));
        event.setMrState(pr.getString("state"));
        event.setAuthorId(author != null ? author.getString("id") : null);
        event.setAuthorName(author != null ? author.getString("login") : null);
        event.setSourceBranch(head != null ? head.getString("ref") : json.getString("source_branch"));
        event.setTargetBranch(base != null ? base.getString("ref") : json.getString("target_branch"));
        event.setLastCommitSha(head != null ? head.getString("sha") : null);
        return event;
    }

    @Override
    public boolean verifyWebhookSignature(ScmConfig config, Map<String, String> headers, String payload) {
        return matchToken(config.getWebhookSecret(), headers.getOrDefault("x-gitee-token", ""));
    }

    @Override
    public List<DiffFile> getPullRequestDiffs(ScmConfig config, ReviewTask task) {
        String url = apiBaseUrl(config) + "/repos/{owner}/{repo}/pulls/{number}/files";
        ResponseEntity<String> response = doGet(urlWithToken(url, config), buildHeaders(config),
                task.getRepoOwner(), task.getRepoName(), task.getMrIid(), config.getAccessToken());
        JSONArray files = JSON.parseArray(response.getBody());
        List<DiffFile> result = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            JSONObject item = files.getJSONObject(i);
            DiffFile diffFile = new DiffFile();
            diffFile.setOldPath(item.getString("prev_filename"));
            diffFile.setNewPath(item.getString("filename"));
            String status = item.getString("status");
            diffFile.setNewFile("added".equals(status));
            diffFile.setDeletedFile("removed".equals(status));
            diffFile.setRenamedFile("renamed".equals(status));
            diffFile.setDiff(item.getString("patch"));
            result.add(diffFile);
        }
        return result;
    }

    @Override
    public String getFileContent(ScmConfig config, ReviewTask task, String filePath, String ref) {
        String url = apiBaseUrl(config) + "/repos/{owner}/{repo}/contents/" + normalizeContentPath(filePath) + "?ref={ref}&access_token={token}";
        try {
            return doGet(url, buildHeaders(config),
                    task.getRepoOwner(), task.getRepoName(), ref, config.getAccessToken()).getBody();
        } catch (RestClientException | com.lnzz.argus.common.exception.BizException e) {
            return null;
        }
    }

    @Override
    public Long addPullRequestComment(ScmConfig config, ReviewTask task, String body) {
        String url = apiBaseUrl(config) + "/repos/{owner}/{repo}/pulls/{number}/comments?access_token={token}";
        ResponseEntity<String> response = doPost(url, buildHeaders(config), Map.of("body", body),
                task.getRepoOwner(), task.getRepoName(), task.getMrIid(), config.getAccessToken());
        return JSONObject.parseObject(response.getBody()).getLong("id");
    }

    @Override
    public void setPullRequestLabels(ScmConfig config, ReviewTask task, List<String> labels) {
        String url = apiBaseUrl(config) + "/repos/{owner}/{repo}/labels?access_token={token}";
        doPost(url, buildHeaders(config), List.of(Map.of("name", labels.get(0))),
                task.getRepoOwner(), task.getRepoName(), config.getAccessToken());
    }

    private String urlWithToken(String url, ScmConfig config) {
        return url + "?access_token={token}";
    }

    private HttpHeaders buildHeaders(ScmConfig config) {
        HttpHeaders headers = jsonHeaders();
        if (isText(config.getAccessToken())) {
            headers.setBearerAuth(config.getAccessToken());
        }
        return headers;
    }

    @Override
    protected String apiBaseUrl(ScmConfig config) {
        return defaultIfBlank(config.getApiBaseUrl(), scmProperties.getGitee().getApiBaseUrl());
    }

    @Override
    protected String webBaseUrl(ScmConfig config) {
        return defaultIfBlank(config.getWebBaseUrl(), scmProperties.getGitee().getWebBaseUrl());
    }

    private String normalizeContentPath(String filePath) {
        return encodePath(filePath).replace("%2F", "/");
    }
}
