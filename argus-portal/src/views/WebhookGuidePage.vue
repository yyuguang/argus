<template>
  <section class="page">
    <div class="page-banner compact">
      <div>
        <p class="eyebrow">Webhook Setup</p>
        <h2>Webhook 接入指引</h2>
        <p class="page-copy">
          仓库配置保存后，在对应平台控制台中配置回调地址和签名密钥。
          Argus 会根据平台和仓库身份自动匹配配置，并触发代码评审任务。
        </p>
      </div>
    </div>

    <div class="guide-grid">
      <article class="guide-card">
        <h3>GitLab</h3>
        <p>回调地址</p>
        <code>/api/v1/webhook/gitlab</code>
        <ul class="timeline-list">
          <li>事件选择 `Merge request events`</li>
          <li>Secret Token 填写你在仓库配置中保存的 `Webhook Secret`</li>
          <li>推荐使用项目 ID 与 group/repo 同时建档，便于精确匹配</li>
        </ul>
      </article>

      <article class="guide-card">
        <h3>GitHub</h3>
        <p>回调地址</p>
        <code>/api/v1/webhook/github</code>
        <ul class="timeline-list">
          <li>事件选择 `Pull requests`</li>
          <li>Secret 使用后端仓库配置中的 `Webhook Secret`</li>
          <li>Token 推荐具备代码读取、PR 评论与标签写入权限</li>
        </ul>
      </article>

      <article class="guide-card">
        <h3>Gitee</h3>
        <p>回调地址</p>
        <code>/api/v1/webhook/gitee</code>
        <ul class="timeline-list">
          <li>事件选择 `Pull Request Hook`</li>
          <li>Token/密码按 Gitee Webhook 配置项与平台约定填写</li>
          <li>建议维护 owner/repo，避免仅靠仓库名称匹配</li>
        </ul>
      </article>
    </div>
  </section>
</template>
