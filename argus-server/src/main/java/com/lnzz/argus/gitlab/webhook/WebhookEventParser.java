package com.lnzz.argus.gitlab.webhook;

import com.alibaba.fastjson2.JSONObject;
import com.lnzz.argus.gitlab.model.MergeRequestEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * GitLab Webhook 事件解析器
 * <p>将 GitLab 原始 Webhook Payload 解析为内部 MergeRequestEvent</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class WebhookEventParser {

    /**
     * 解析 MR 事件
     *
     * @param payload Webhook 原始请求体
     * @return MR 事件对象，非 MR 事件返回 null
     */
    public MergeRequestEvent parseMergeRequestEvent(String payload) {
        JSONObject json = JSONObject.parseObject(payload);
        String objectKind = json.getString("object_kind");

        if (!"merge_request".equals(objectKind)) {
            log.debug("非MR事件，忽略: objectKind={}", objectKind);
            return null;
        }

        JSONObject project = json.getJSONObject("project");
        JSONObject attrs = json.getJSONObject("object_attributes");
        JSONObject user = json.getJSONObject("user");
        JSONObject lastCommit = attrs.getJSONObject("last_commit");

        MergeRequestEvent event = new MergeRequestEvent();
        event.setEventType(attrs.getString("action"));
        event.setProjectId(project.getLong("id"));
        event.setProjectName(project.getString("name"));
        event.setProjectUrl(project.getString("web_url"));
        event.setMrIid(attrs.getLong("iid"));
        event.setMrTitle(attrs.getString("title"));
        event.setMrUrl(attrs.getString("url"));
        event.setMrState(attrs.getString("state"));
        event.setAuthorId(user.getLong("id"));
        event.setAuthorName(user.getString("name"));
        event.setSourceBranch(attrs.getString("source_branch"));
        event.setTargetBranch(attrs.getString("target_branch"));

        if (lastCommit != null) {
            event.setLastCommitSha(lastCommit.getString("id"));
        }

        log.info("解析MR事件: project={}, mrIid={}, action={}, author={}, {}→{}",
                event.getProjectName(), event.getMrIid(), event.getEventType(),
                event.getAuthorName(), event.getSourceBranch(), event.getTargetBranch());

        return event;
    }
}
