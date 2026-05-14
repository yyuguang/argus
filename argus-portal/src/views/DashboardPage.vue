<template>
  <section class="page">
    <div class="page-banner">
      <div>
        <p class="eyebrow">Operations Overview</p>
        <h2>Argus 企业治理总览</h2>
        <p class="page-copy">
          这里展示平台当前的仓库接入能力、评审执行链路与运营准备状态。
          后续可以继续扩展任务统计、告警看板、评审趋势和失败重试中心。
        </p>
      </div>
      <div class="banner-note">
        <span>当前阶段</span>
        <strong>SCM 接入后台已就绪</strong>
        <p>建议下一步补充登录鉴权、字典配置和任务查询页。</p>
      </div>
    </div>

    <div class="stats-grid">
      <article class="stat-card">
        <span class="stat-label">支持平台</span>
        <strong class="stat-value">3</strong>
        <p>GitLab / GitHub / Gitee</p>
      </article>
      <article class="stat-card">
        <span class="stat-label">配置中心</span>
        <strong class="stat-value">DB</strong>
        <p>仓库地址、Token、Webhook Secret 统一落库管理</p>
      </article>
      <article class="stat-card">
        <span class="stat-label">接入路由</span>
        <strong class="stat-value">1</strong>
        <p>统一入口 `/api/v1/webhook/{provider}`</p>
      </article>
    </div>

    <div class="overview-grid">
      <section class="panel-card">
        <div class="section-heading">
          <div>
            <h3>当前已落地模块</h3>
            <p>便于公司内部继续扩展为标准治理后台。</p>
          </div>
        </div>
        <ul class="timeline-list">
          <li>统一 SCM 平台抽象与 GitLab / GitHub / Gitee 三方实现</li>
          <li>仓库配置表与配置管理接口</li>
          <li>标准后台左侧菜单、顶部导航、内容工作区</li>
          <li>仓库配置录入页与 Webhook 指引页</li>
        </ul>
      </section>

      <section class="panel-card">
        <div class="section-heading">
          <div>
            <h3>推荐后续菜单</h3>
            <p>你后面继续做时可以按这个体系扩展。</p>
          </div>
        </div>
        <ul class="timeline-list">
          <li>评审任务中心：任务状态、评分、失败原因、重试</li>
          <li>规则管理：规范文档、提示词模板、评分阈值</li>
          <li>通知中心：企微 webhook、发送记录、频控</li>
          <li>系统管理：账号、角色、审计日志、环境参数</li>
        </ul>
      </section>
    </div>
  </section>
</template>
