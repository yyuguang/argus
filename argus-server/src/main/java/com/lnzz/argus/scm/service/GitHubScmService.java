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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * GitHub 平台服务
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
public class GitHubScmService extends AbstractScmPlatformService {

    private static final String GITHUB_API_VERSION = "2022-11-28";

    public GitHubScmService(ScmProperties scmProperties) {
        super(scmProperties);
    }

    @Override
    public String getProvider() {
        return "github";
    }

    @Override
    public PullRequestEvent parseWebhookEvent(Map<String, String> headers, String payload) {
        if (!"pull_request".equalsIgnoreCase(headers.getOrDefault("x-github-event", ""))) {
            return null;
        }

        JSONObject json = JSONObject.parseObject(payload);
        JSONObject repository = json.getJSONObject("repository");
        JSONObject pr = json.getJSONObject("pull_request");
        JSONObject head = pr.getJSONObject("head");
        JSONObject base = pr.getJSONObject("base");
        JSONObject user = pr.getJSONObject("user");

        PullRequestEvent event = new PullRequestEvent();
        event.setScmProvider(getProvider());
        event.setEventType(json.getString("action"));
        event.setProjectId(repository.getLong("id"));
        event.setProjectName(repository.getString("full_name"));
        event.setProjectUrl(repository.getString("html_url"));
        event.setRepoOwner(repository.getJSONObject("owner").getString("login"));
        event.setRepoName(repository.getString("name"));
        event.setMrIid(pr.getLong("number"));
        event.setMrTitle(pr.getString("title"));
        event.setMrUrl(pr.getString("html_url"));
        event.setMrState(pr.getString("state"));
        event.setAuthorId(user.getString("id"));
        event.setAuthorName(user.getString("login"));
        event.setSourceBranch(head.getString("ref"));
        event.setTargetBranch(base.getString("ref"));
        event.setLastCommitSha(head.getString("sha"));
        return event;
    }

    @Override
    public boolean verifyWebhookSignature(ScmConfig config, Map<String, String> headers, String payload) {
        String secret = config.getWebhookSecret();
        String signature = headers.getOrDefault("x-hub-signature-256", "");
        if (!isText(secret)) {
            return true;
        }
        if (!signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String digest = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return ("sha256=" + digest).equals(signature);
        } catch (Exception e) {
            throw buildScmException("VERIFY", "github-webhook", e);
        }
    }

    @Override
    public List<DiffFile> getPullRequestDiffs(ScmConfig config, ReviewTask task) {
        String url = apiBaseUrl(config) + "/repos/{owner}/{repo}/pulls/{number}/files?per_page=100";
        ResponseEntity<String> response = doGet(url, buildHeaders(config), task.getRepoOwner(), task.getRepoName(), task.getMrIid());
        JSONArray files = JSON.parseArray(response.getBody());
        List<DiffFile> result = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            JSONObject item = files.getJSONObject(i);
            DiffFile diffFile = new DiffFile();
            diffFile.setOldPath(item.getString("previous_filename"));
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
        String url = apiBaseUrl(config) + "/repos/{owner}/{repo}/contents/" + normalizeContentPath(filePath) + "?ref={ref}";
        HttpHeaders headers = buildHeaders(config);
        headers.setAccept(List.of(org.springframework.http.MediaType.valueOf("application/vnd.github.raw+json")));
        try {
            return doGet(url, headers, task.getRepoOwner(), task.getRepoName(), ref).getBody();
        } catch (RestClientException | com.lnzz.argus.common.exception.BizException e) {
            return null;
        }
    }

    @Override
    public Long addPullRequestComment(ScmConfig config, ReviewTask task, String body) {
        String url = apiBaseUrl(config) + "/repos/{owner}/{repo}/issues/{number}/comments";
        ResponseEntity<String> response = doPost(url, buildHeaders(config), Map.of("body", body),
                task.getRepoOwner(), task.getRepoName(), task.getMrIid());
        return JSONObject.parseObject(response.getBody()).getLong("id");
    }

    @Override
    public void setPullRequestLabels(ScmConfig config, ReviewTask task, List<String> labels) {
        String url = apiBaseUrl(config) + "/repos/{owner}/{repo}/issues/{number}/labels";
        doPost(url, buildHeaders(config), Map.of("labels", labels), task.getRepoOwner(), task.getRepoName(), task.getMrIid());
    }

    private HttpHeaders buildHeaders(ScmConfig config) {
        HttpHeaders headers = jsonHeaders();
        if (isText(config.getAccessToken())) {
            headers.setBearerAuth(config.getAccessToken());
        }
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", GITHUB_API_VERSION);
        return headers;
    }

    @Override
    protected String apiBaseUrl(ScmConfig config) {
        return defaultIfBlank(config.getApiBaseUrl(), scmProperties.getGithub().getApiBaseUrl());
    }

    @Override
    protected String webBaseUrl(ScmConfig config) {
        return defaultIfBlank(config.getWebBaseUrl(), scmProperties.getGithub().getWebBaseUrl());
    }

    private String normalizeContentPath(String filePath) {
        return encodePath(filePath).replace("%2F", "/");
    }
}
