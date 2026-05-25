package com.lnzz.argus.scm.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.config.ScmProperties;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.model.PullRequestEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GitLab 平台服务
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
public class GitLabScmService extends AbstractScmPlatformService {

    public GitLabScmService(ScmProperties scmProperties) {
        super(scmProperties, scmProperties == null ? null : scmProperties.getGitlab());
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
    public String getFileContent(ScmConfig config, String filePath, String ref) {
        String url = apiBaseUrl(config) + "/projects/{projectId}/repository/files/{filePath}/raw?ref={ref}";
        return doGet(url, buildHeaders(config), config.getProjectId(), encodePath(filePath), ref).getBody();
    }

    @Override
    public List<String> listRepositoryFiles(ScmConfig config, String ref) {
        List<String> result = new ArrayList<>();
        int page = 1;
        while (true) {
            String url = apiBaseUrl(config)
                    + "/projects/{projectId}/repository/tree?ref={ref}&recursive=true&per_page=100&page={page}";
            ResponseEntity<String> response = doGet(url, buildHeaders(config), config.getProjectId(), ref, page);
            JSONArray tree = JSON.parseArray(response.getBody());
            for (int i = 0; i < tree.size(); i++) {
                JSONObject item = tree.getJSONObject(i);
                if ("blob".equals(item.getString("type")) && item.getString("path") != null) {
                    result.add(item.getString("path"));
                }
            }
            String nextPage = response.getHeaders().getFirst("X-Next-Page");
            if (nextPage == null || nextPage.isBlank()) {
                break;
            }
            page = Integer.parseInt(nextPage);
        }
        return result;
    }

    @Override
    public boolean materializeRepositoryFiles(ScmConfig config,
                                              String ref,
                                              Predicate<String> fileFilter,
                                              Path repositoryRoot,
                                              Collection<String> loadedFilePaths,
                                              Collection<String> failedFilePaths,
                                              Collection<String> warnings) {
        long startedAt = System.currentTimeMillis();
        byte[] archiveBytes = downloadRepositoryArchive(config, ref);
        validateArchiveSize(archiveBytes);
        int entryCount = extractRepositoryArchive(
                archiveBytes, repositoryRoot, fileFilter, loadedFilePaths, failedFilePaths, warnings);
        log.info("GitLab 仓库 archive 快照物化完成, projectId={}, ref={}, entryCount={}, loadedFileCount={}, failedFileCount={}, costMs={}",
                config.getProjectId(), ref, entryCount, loadedFilePaths.size(), failedFilePaths.size(),
                System.currentTimeMillis() - startedAt);
        return true;
    }

    protected byte[] downloadRepositoryArchive(ScmConfig config, String ref) {
        String url = apiBaseUrl(config) + "/projects/{projectId}/repository/archive.zip?sha={ref}";
        ResponseEntity<byte[]> response = doGetBytes(url, buildHeaders(config), config.getProjectId(), ref);
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new BizException(ResultCode.SCM_ERROR, "GitLab repository archive 响应为空");
        }
        return body;
    }

    private void validateArchiveSize(byte[] archiveBytes) {
        int maxArchiveSize = scmProperties.getGitlab().getMaxArchiveSize();
        if (maxArchiveSize > 0 && archiveBytes.length > maxArchiveSize) {
            throw new BizException(ResultCode.SCM_ERROR,
                    "GitLab repository archive 超过大小限制: " + archiveBytes.length + " > " + maxArchiveSize);
        }
    }

    private int extractRepositoryArchive(byte[] archiveBytes,
                                         Path repositoryRoot,
                                         Predicate<String> fileFilter,
                                         Collection<String> loadedFilePaths,
                                         Collection<String> failedFilePaths,
                                         Collection<String> warnings) {
        int entryCount = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                if (entry.isDirectory()) {
                    continue;
                }
                String filePath = stripArchiveRoot(entry.getName());
                if (!isText(filePath) || (fileFilter != null && !fileFilter.test(filePath))) {
                    continue;
                }
                Path targetPath = resolveArchiveTarget(repositoryRoot, filePath);
                if (targetPath == null) {
                    failedFilePaths.add(filePath);
                    warnings.add("GitLab archive 包含非法文件路径: " + filePath);
                    continue;
                }
                materializeArchiveEntry(zipInputStream, targetPath, filePath, loadedFilePaths, failedFilePaths, warnings);
            }
            return entryCount;
        } catch (IOException e) {
            throw new BizException(ResultCode.SCM_ERROR, "GitLab repository archive 解压失败: " + e.getMessage());
        }
    }

    private void materializeArchiveEntry(ZipInputStream zipInputStream,
                                         Path targetPath,
                                         String filePath,
                                         Collection<String> loadedFilePaths,
                                         Collection<String> failedFilePaths,
                                         Collection<String> warnings) {
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(zipInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            loadedFilePaths.add(filePath);
        } catch (IOException e) {
            failedFilePaths.add(filePath);
            warnings.add("GitLab archive 文件写入失败: " + filePath + " - " + e.getMessage());
        }
    }

    private String stripArchiveRoot(String entryName) {
        if (!isText(entryName)) {
            return "";
        }
        String normalizedEntryName = entryName.replace('\\', '/').replaceFirst("^/+", "");
        int firstSlash = normalizedEntryName.indexOf('/');
        if (firstSlash < 0 || firstSlash == normalizedEntryName.length() - 1) {
            return "";
        }
        return normalizedEntryName.substring(firstSlash + 1);
    }

    private Path resolveArchiveTarget(Path repositoryRoot, String filePath) {
        Path targetPath = repositoryRoot.resolve(filePath).normalize();
        return targetPath.startsWith(repositoryRoot) ? targetPath : null;
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
