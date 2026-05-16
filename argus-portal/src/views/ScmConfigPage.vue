<template>
  <section class="page scm-page">
    <div class="scm-titlebar">
      <div>
        <p class="eyebrow">Repository Governance</p>
        <h2>SCM 配置管理</h2>
        <p>
          统一管理 GitLab、GitHub、Gitee 仓库接入、Webhook 鉴权、代码解析规则和仓库级 AI 评审策略。
        </p>
      </div>
      <div class="title-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadConfigs">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDrawer">新增仓库</el-button>
      </div>
    </div>

    <div class="scm-stats">
      <article class="scm-stat">
        <span>已接入仓库</span>
        <strong>{{ configs.length }}</strong>
        <small>全部 SCM 配置</small>
      </article>
      <article class="scm-stat">
        <span>启用中</span>
        <strong>{{ enabledCount }}</strong>
        <small>可匹配 Webhook</small>
      </article>
      <article class="scm-stat">
        <span>触发规则</span>
        <strong>{{ triggerConfiguredCount }}</strong>
        <small>已配置 reviewConfig.trigger</small>
      </article>
      <article class="scm-stat">
        <span>通知开启</span>
        <strong>{{ notifyEnabledCount }}</strong>
        <small>SCM Webhook 可发送</small>
      </article>
      <article class="scm-stat">
        <span>绑定应用</span>
        <strong>{{ linkedAppCount }}</strong>
        <small>已配置 appName 映射</small>
      </article>
    </div>

    <section class="panel-card scm-workbench">
      <div class="scm-toolbar">
        <div class="toolbar-filters">
          <el-select v-model="filters.provider" clearable placeholder="全部平台" style="width: 150px">
            <el-option label="GitLab" value="gitlab" />
            <el-option label="GitHub" value="github" />
            <el-option label="Gitee" value="gitee" />
          </el-select>
          <el-select v-model="filters.enabled" clearable placeholder="全部状态" style="width: 150px">
            <el-option label="启用" value="enabled" />
            <el-option label="停用" value="disabled" />
          </el-select>
          <el-input
            v-model.trim="filters.keyword"
            clearable
            placeholder="搜索仓库、Owner、API 地址"
            style="width: 280px"
          />
        </div>
        <div class="toolbar-summary">
          当前展示 <strong>{{ filteredConfigs.length }}</strong> 条配置
        </div>
      </div>

      <el-alert
        v-if="errorMessage"
        class="scm-alert"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
      />

      <el-table :data="filteredConfigs" v-loading="loading" border class="scm-table">
        <el-table-column label="仓库" min-width="280" fixed>
          <template #default="{ row }">
            <div class="repo-cell">
              <div>
                <strong>{{ row.projectName || composeRepoName(row) }}</strong>
                <span>{{ composeRepoName(row) }}</span>
              </div>
              <el-tag :type="providerTagType(row.scmProvider)" effect="light">
                {{ providerLabel(row.scmProvider) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="接入定位" min-width="180">
          <template #default="{ row }">
            <div class="table-main">{{ row.projectId ? `Project ID: ${row.projectId}` : 'Owner / Repo' }}</div>
            <div class="table-sub">{{ row.repoOwner && row.repoName ? `${row.repoOwner}/${row.repoName}` : '-' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="触发规则" min-width="240">
          <template #default="{ row }">
            <div class="table-main">{{ triggerSummary(row) }}</div>
            <div class="table-sub">{{ branchSummary(row) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="评审策略" min-width="220">
          <template #default="{ row }">
            <div class="table-main">阈值 {{ scoreThreshold(row) }} 分</div>
            <div class="table-sub">文件上限 {{ maxReviewFiles(row) }}，并发 {{ row.reviewParallelism || 3 }}</div>
          </template>
        </el-table-column>

        <el-table-column label="通知" min-width="150">
          <template #default="{ row }">
            <el-tag :type="notifyState(row).type" effect="light">
              {{ notifyState(row).label }}
            </el-tag>
            <div class="table-sub">{{ notifyState(row).description }}</div>
          </template>
        </el-table-column>

        <el-table-column label="绑定应用" min-width="220">
          <template #default="{ row }">
            <div class="app-tags">
              <el-tag
                v-for="mapping in mappingsForConfig(row).slice(0, 3)"
                :key="mapping.id"
                size="small"
                effect="plain"
              >
                {{ mapping.appName }}
              </el-tag>
              <el-tag v-if="mappingsForConfig(row).length > 3" size="small" type="info" effect="plain">
                +{{ mappingsForConfig(row).length - 3 }}
              </el-tag>
              <span v-if="!mappingsForConfig(row).length" class="muted-inline">未绑定</span>
            </div>
            <div class="table-sub">{{ mappingHealthText(row) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="源码定位" min-width="200">
          <template #default="{ row }">
            <el-tag :type="sourceHealth(row).type" effect="light">
              {{ sourceHealth(row).label }}
            </el-tag>
            <div class="table-sub">{{ sourceHealth(row).description }}</div>
          </template>
        </el-table-column>

        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-switch
              :model-value="Boolean(row.enabled)"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="toggleEnabled(row)"
            />
          </template>
        </el-table-column>

        <el-table-column label="API 地址" min-width="260">
          <template #default="{ row }">
            <el-tooltip :content="row.apiBaseUrl || providerDefaults[row.scmProvider]?.apiBaseUrl || '-'" placement="top">
              <span class="ellipsis-text">{{ row.apiBaseUrl || providerDefaults[row.scmProvider]?.apiBaseUrl || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="操作" fixed="right" width="150">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEditDrawer(row)">编辑</el-button>
            <el-dropdown trigger="click">
              <el-button link :icon="MoreFilled">更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :icon="CopyDocument" @click="copyWebhook(row)">复制 Webhook 地址</el-dropdown-item>
                  <el-dropdown-item :icon="View" @click="openDetailDrawer(row)">查看详情</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !filteredConfigs.length" description="暂无匹配的仓库配置" />
    </section>

    <el-drawer
      v-model="drawerVisible"
      :title="drawerMode === 'create' ? '新增仓库配置' : '编辑仓库配置'"
      size="820px"
      destroy-on-close
      class="scm-drawer"
    >
      <el-form ref="formRef" :model="form" label-position="top" class="scm-form">
        <el-tabs v-model="activeTab">
          <el-tab-pane name="basic">
            <template #label>
              <span class="tab-label"><el-icon><Connection /></el-icon>基础信息</span>
            </template>

            <div class="form-section">
              <div class="section-title">
                <h3>仓库定位</h3>
                <p>项目 ID 与 Owner/Repo 至少填写一种，用于 Webhook 事件匹配。</p>
              </div>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="SCM 平台" required>
                    <el-segmented v-model="form.scmProvider" :options="providerOptions" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="项目 ID">
                    <el-input-number v-model="form.projectId" :min="0" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="启用状态">
                    <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="仓库归属">
                    <el-input v-model.trim="form.repoOwner" placeholder="例如 lnzz-team" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="仓库名称">
                    <el-input v-model.trim="form.repoName" placeholder="例如 argus" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-form-item label="仓库显示名称">
                <el-input v-model.trim="form.projectName" placeholder="例如 lnzz-team/argus" />
              </el-form-item>
            </div>

            <div class="form-section">
              <div class="section-title">
                <h3>服务地址</h3>
                <p>留空时使用后端平台默认值；私有化部署建议显式配置。</p>
              </div>
              <el-form-item label="API Base URL">
                <el-input v-model.trim="form.apiBaseUrl" :placeholder="providerDefaults[form.scmProvider].apiBaseUrl" />
              </el-form-item>
              <el-form-item label="Web Base URL">
                <el-input v-model.trim="form.webBaseUrl" :placeholder="providerDefaults[form.scmProvider].webBaseUrl" />
              </el-form-item>
              <el-form-item label="备注说明">
                <el-input v-model.trim="form.description" type="textarea" :rows="3" placeholder="例如：核心业务仓库 / 测试环境接入" />
              </el-form-item>
            </div>
          </el-tab-pane>

          <el-tab-pane name="secret">
            <template #label>
              <span class="tab-label"><el-icon><Key /></el-icon>鉴权</span>
            </template>
            <div class="form-section">
              <div class="section-title">
                <h3>访问凭证</h3>
                <p>编辑时留空表示保留原值。密钥只会脱敏展示，不会在列表中明文显示。</p>
              </div>
              <el-form-item label="访问 Token">
                <el-input
                  v-model.trim="form.accessToken"
                  type="password"
                  show-password
                  :placeholder="drawerMode === 'edit' ? '留空表示保留原有 Token' : '输入访问 Token'"
                />
              </el-form-item>
              <el-form-item label="Webhook Secret">
                <el-input
                  v-model.trim="form.webhookSecret"
                  type="password"
                  show-password
                  :placeholder="drawerMode === 'edit' ? '留空表示保留原有 Secret' : '输入 Webhook Secret'"
                />
              </el-form-item>
              <el-form-item label="Webhook 地址">
                <el-input :model-value="webhookUrl" readonly>
                  <template #append>
                    <el-button :icon="CopyDocument" @click="copyText(webhookUrl)">复制</el-button>
                  </template>
                </el-input>
              </el-form-item>
            </div>
          </el-tab-pane>

          <el-tab-pane name="trigger">
            <template #label>
              <span class="tab-label"><el-icon><Setting /></el-icon>触发规则</span>
            </template>
            <div class="form-section">
              <div class="section-title">
                <h3>评审触发规则</h3>
                <p>用于替代代码里的目标分支写死逻辑，按仓库独立控制 PR/MR 是否进入 AI 评审。</p>
              </div>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="启用触发">
                    <el-switch v-model="form.reviewConfig.trigger.enabled" />
                  </el-form-item>
                </el-col>
                <el-col :span="16">
                  <el-form-item label="分支模式">
                    <el-segmented v-model="form.reviewConfig.trigger.branchMode" :options="branchModeOptions" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="事件类型">
                <el-checkbox-group v-model="form.reviewConfig.trigger.eventTypes">
                  <el-checkbox value="opened">opened</el-checkbox>
                  <el-checkbox value="update">update</el-checkbox>
                  <el-checkbox value="synchronize">synchronize</el-checkbox>
                  <el-checkbox value="reopened">reopened</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-form-item label="目标分支">
                <el-select
                  v-model="form.reviewConfig.trigger.targetBranches"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  placeholder="例如 test、develop、release/*"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item v-if="form.reviewConfig.trigger.branchMode === 'SOURCE_AND_TARGET'" label="源分支">
                <el-select
                  v-model="form.reviewConfig.trigger.sourceBranches"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  placeholder="例如 dev、feature/*、bugfix/*"
                  style="width: 100%"
                />
              </el-form-item>
            </div>
          </el-tab-pane>

          <el-tab-pane name="parser">
            <template #label>
              <span class="tab-label"><el-icon><Files /></el-icon>代码解析</span>
            </template>
            <div class="form-section">
              <div class="section-title">
                <h3>代码解析策略</h3>
                <p>用于多模块仓库的源码定位、关联类提取和上下文构建。</p>
                <el-button size="small" @click="fillArgusExample">填入 Argus 示例</el-button>
              </div>
              <el-form-item label="基础包列表 JSON">
                <el-input v-model.trim="form.basePackages" type="textarea" :rows="3" placeholder='["com.lnzz.argus"]' />
              </el-form-item>
              <el-form-item label="模块源码根 JSON">
                <el-input v-model.trim="form.moduleSourceRoots" type="textarea" :rows="4" placeholder='["argus-server/src/main/java"]' />
              </el-form-item>
              <el-form-item label="包到模块映射 JSON">
                <el-input
                  v-model.trim="form.packageModuleMappings"
                  type="textarea"
                  :rows="6"
                  placeholder='[{"packagePrefix":"com.demo.common","sourceRoot":"demo-common/src/main/java"}]'
                />
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="最大关联类数">
                    <el-input-number v-model="form.maxRelatedClasses" :min="1" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="最大上下文 Token">
                    <el-input-number v-model="form.maxContextTokens" :min="1000" :step="1000" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="评审并发度">
                    <el-input-number v-model="form.reviewParallelism" :min="1" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>
          </el-tab-pane>

          <el-tab-pane name="filter">
            <template #label>
              <span class="tab-label"><el-icon><DataAnalysis /></el-icon>过滤与评分</span>
            </template>
            <div class="form-section">
              <div class="section-title">
                <h3>文件过滤与 Token 预算</h3>
                <p>控制大文件、锁文件、二进制资源和上下文预算，减少无效 AI 消耗。</p>
              </div>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="单文件最大 diff 行数">
                    <el-input-number v-model="form.reviewConfig.fileFilter.maxDiffLinesPerFile" :min="1" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="最大总 diff 行数">
                    <el-input-number v-model="form.reviewConfig.fileFilter.maxTotalDiffLines" :min="1" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="最大评审文件数">
                    <el-input-number v-model="form.reviewConfig.fileFilter.maxReviewFiles" :min="1" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="排除文件模式">
                <el-select v-model="form.reviewConfig.fileFilter.excludeFilePatterns" multiple filterable allow-create default-first-option style="width: 100%" />
              </el-form-item>
              <el-form-item label="二进制扩展名">
                <el-select v-model="form.reviewConfig.fileFilter.binaryExtensions" multiple filterable allow-create default-first-option style="width: 100%" />
              </el-form-item>
            </div>

            <div class="form-section">
              <div class="section-title">
                <h3>评分与异步策略</h3>
                <p>控制最终分计算、阻止阈值、评分超时和进度评论。</p>
              </div>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="阻止阈值">
                    <el-input-number v-model="form.reviewConfig.scoring.blockThreshold" :min="0" :max="100" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="AI 分权重">
                    <el-input-number v-model="form.reviewConfig.scoring.aiWeight" :min="0" :max="1" :step="0.1" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="规则分权重">
                    <el-input-number v-model="form.reviewConfig.scoring.ruleWeight" :min="0" :max="1" :step="0.1" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-alert v-if="scoreWeightWarning" type="warning" show-icon :closable="false" title="AI 分权重与规则分权重建议合计为 1" />
              <el-row :gutter="16" class="dimension-row">
                <el-col v-for="item in dimensionFields" :key="item.key" :span="8">
                  <el-form-item :label="item.label">
                    <el-input-number v-model="form.reviewConfig.scoring.dimensions[item.key]" :min="0" :max="100" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-alert v-if="dimensionWeightWarning" type="warning" show-icon :closable="false" title="五维度权重建议合计为 100" />
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="评分超时秒数">
                    <el-input-number v-model="form.reviewConfig.async.scoreTimeoutSec" :min="10" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="线程池大小">
                    <el-input-number v-model="form.reviewConfig.async.threadPoolSize" :min="1" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="进度评论">
                    <el-switch v-model="form.reviewConfig.async.progressCommentEnabled" />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>
          </el-tab-pane>

          <el-tab-pane name="notify">
            <template #label>
              <span class="tab-label"><el-icon><Bell /></el-icon>通知</span>
            </template>
            <div class="form-section">
              <div class="section-title">
                <h3>仓库级通知策略</h3>
                <p>企业微信配置只从 SCM 配置读取，同时影响 PR 评审通知与错误日志告警。</p>
              </div>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="仓库级企微通知">
                    <el-switch v-model="form.wechatNotifyEnabled" active-text="开启" inactive-text="关闭" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="评审通知开关">
                    <el-switch v-model="form.reviewConfig.notification.wechatNotifyEnabled" active-text="开启" inactive-text="关闭" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="自定义企微 Webhook">
                <el-input
                  v-model.trim="form.wechatNotifyWebhook"
                  type="password"
                  show-password
                  :placeholder="drawerMode === 'edit' && form.existingWechatWebhookConfigured ? '留空表示保留原 SCM Webhook' : '请输入 SCM 企业微信机器人 Webhook'"
                />
              </el-form-item>
              <el-alert
                v-if="form.wechatNotifyEnabled && !form.wechatNotifyWebhook && !(drawerMode === 'edit' && form.existingWechatWebhookConfigured)"
                type="warning"
                show-icon
                :closable="false"
                title="开启企业微信通知后必须配置 Webhook，否则 PR 评审通知和错误日志告警都会跳过"
              />
              <el-form-item label="低分告警阈值">
                <el-input-number v-model="form.reviewConfig.notification.scoreAlertThreshold" :min="0" :max="100" controls-position="right" />
              </el-form-item>
            </div>

            <div class="form-section">
              <div class="section-title">
                <h3>通知重试策略</h3>
                <p>控制企业微信发送失败后的重试次数、退避间隔和总超时窗口。</p>
              </div>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="最大重试次数">
                    <el-input-number
                      v-model="form.reviewConfig.notification.retry.maxRetries"
                      :min="0"
                      :max="10"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="总超时秒数">
                    <el-input-number
                      v-model="form.reviewConfig.notification.retry.timeoutSec"
                      :min="1"
                      :max="3600"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="退避秒数">
                    <el-select
                      v-model="form.reviewConfig.notification.retry.backoffSeconds"
                      multiple
                      filterable
                      allow-create
                      default-first-option
                      style="width: 100%"
                    >
                      <el-option v-for="item in retryBackoffOptions" :key="item" :label="`${item}s`" :value="item" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
            </div>

            <div class="form-section">
              <div class="section-title">
                <h3>错误日志告警路由</h3>
                <p>控制错误分析完成后各等级是否推送企业微信，保存后立即跟随当前 SCM 配置生效。</p>
              </div>
              <el-table :data="errorSeverityOptions" border class="route-table">
                <el-table-column label="等级" width="110">
                  <template #default="{ row }">
                    <el-tag :type="row.type" effect="light">{{ row.value }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="label" label="说明" min-width="150" />
                <el-table-column label="是否推送" min-width="150">
                  <template #default="{ row }">
                    <el-switch
                      v-model="form.reviewConfig.notification.errorAlertRoutes[row.value].enabled"
                      active-text="推送"
                      inactive-text="抑制"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="通道" min-width="150">
                  <template #default="{ row }">
                    <el-select v-model="form.reviewConfig.notification.errorAlertRoutes[row.value].channel">
                      <el-option v-for="item in errorRouteChannelOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="优先级" min-width="150">
                  <template #default="{ row }">
                    <el-select v-model="form.reviewConfig.notification.errorAlertRoutes[row.value].priority">
                      <el-option v-for="item in errorRoutePriorityOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane name="linkage">
            <template #label>
              <span class="tab-label"><el-icon><Connection /></el-icon>应用联动</span>
            </template>
            <div class="form-section">
              <div class="section-title">
                <div>
                  <h3>appName 到当前 SCM 仓库的映射</h3>
                  <p>一行代表一个微服务 appName；同一个 SCM 仓库可绑定多个服务，用于源码定位和企业微信告警。</p>
                </div>
                <el-button size="small" type="primary" :disabled="!canEditMappings" @click="addMappingDraft">
                  新增应用映射
                </el-button>
              </div>

              <el-alert
                v-if="!canEditMappings"
                type="warning"
                show-icon
                :closable="false"
                title="请先保存 SCM 配置并确保 projectId 存在，再维护应用联动"
              />

              <el-alert
                v-if="canEditMappings && moduleSourceRootOptions.length > 1"
                type="info"
                show-icon
                :closable="false"
                title="当前 SCM 已配置多个源码根，新增微服务映射时请为每个 appName 选择对应的服务源码根"
              />

              <el-table :data="currentMappings" border class="mapping-table">
                <el-table-column label="服务 appName" min-width="170">
                  <template #default="{ row }">
                    <el-input v-if="row.__editing" v-model.trim="row.appName" placeholder="order-service" />
                    <strong v-else>{{ row.appName }}</strong>
                  </template>
                </el-table-column>
                <el-table-column label="服务源码根" min-width="240">
                  <template #default="{ row }">
                    <el-select
                      v-if="row.__editing"
                      v-model="row.sourceRoot"
                      filterable
                      allow-create
                      default-first-option
                      placeholder="例如 order-service/src/main/java"
                    >
                      <el-option v-for="root in moduleSourceRootOptions" :key="root" :label="root" :value="root" />
                    </el-select>
                    <span v-else>{{ row.sourceRoot || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="服务基础包" min-width="210">
                  <template #default="{ row }">
                    <el-input v-if="row.__editing" v-model.trim="row.basePackage" placeholder="com.demo.order" />
                    <span v-else>{{ row.basePackage || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="默认分支" min-width="130">
                  <template #default="{ row }">
                    <el-input v-if="row.__editing" v-model.trim="row.defaultBranch" placeholder="main" />
                    <span v-else>{{ row.defaultBranch || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="120">
                  <template #default="{ row }">
                    <el-tag :type="mappingHealth(row).type" effect="light">{{ mappingHealth(row).label }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="260" fixed="right">
                  <template #default="{ row }">
                    <template v-if="row.__editing">
                      <el-button link type="primary" @click="saveMapping(row)">保存</el-button>
                      <el-button link @click="cancelMapping(row)">取消</el-button>
                    </template>
                    <template v-else>
                      <el-button link type="primary" @click="openDataMonitorDrawer(row)">数据监控</el-button>
                      <el-button link type="primary" @click="editMapping(row)">编辑</el-button>
                      <el-button link type="danger" @click="removeMapping(row)">删除</el-button>
                    </template>
                  </template>
                </el-table-column>
              </el-table>

              <el-empty v-if="canEditMappings && !currentMappings.length" description="当前 SCM 仓库尚未绑定 appName" />
            </div>
          </el-tab-pane>

          <el-tab-pane name="json">
            <template #label>
              <span class="tab-label"><el-icon><Document /></el-icon>高级 JSON</span>
            </template>
            <div class="form-section">
              <div class="section-title">
                <h3>reviewConfig JSON</h3>
                <p>用于高级配置和排障。启用编辑后，保存时将以这里的 JSON 为准。</p>
                <el-switch v-model="advancedJsonEditing" active-text="编辑 JSON" inactive-text="只读预览" />
              </div>
              <el-input
                v-model="advancedReviewConfigJson"
                type="textarea"
                :rows="18"
                :readonly="!advancedJsonEditing"
                spellcheck="false"
                class="json-editor"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ drawerMode === 'create' ? '创建配置' : '保存修改' }}
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="detailVisible" title="仓库配置详情" size="560px">
      <template v-if="detailConfig">
        <div class="detail-panel">
          <h3>{{ detailConfig.projectName || composeRepoName(detailConfig) }}</h3>
          <p>{{ detailConfig.description || '暂无备注' }}</p>
          <dl>
            <dt>平台</dt>
            <dd>{{ providerLabel(detailConfig.scmProvider) }}</dd>
            <dt>仓库</dt>
            <dd>{{ composeRepoName(detailConfig) }}</dd>
            <dt>Webhook</dt>
            <dd>{{ webhookUrlFor(detailConfig.scmProvider) }}</dd>
            <dt>触发规则</dt>
            <dd>{{ triggerSummary(detailConfig) }}；{{ branchSummary(detailConfig) }}</dd>
            <dt>评审策略</dt>
            <dd>阈值 {{ scoreThreshold(detailConfig) }} 分，最大文件数 {{ maxReviewFiles(detailConfig) }}</dd>
            <dt>通知</dt>
            <dd>{{ notifyState(detailConfig).label }}：{{ notifyState(detailConfig).description }}</dd>
            <dt>绑定应用</dt>
            <dd>
              <span v-if="mappingsForConfig(detailConfig).length">
                {{ mappingsForConfig(detailConfig).map((item) => item.appName).join('，') }}
              </span>
              <span v-else>未绑定 appName</span>
            </dd>
          </dl>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Bell,
  Connection,
  CopyDocument,
  DataAnalysis,
  Document,
  Edit,
  Files,
  Key,
  MoreFilled,
  Plus,
  Refresh,
  Setting,
  View,
} from '@element-plus/icons-vue'
import {
  createProjectMapping,
  createScmConfig,
  deleteProjectMapping,
  fetchProjectMappings,
  fetchScmConfigs,
  updateProjectMapping,
  updateScmConfig,
} from '../api/scm'

const providerDefaults = {
  gitlab: {
    label: 'GitLab',
    apiBaseUrl: 'https://gitlab.com/api/v4',
    webBaseUrl: 'https://gitlab.com',
  },
  github: {
    label: 'GitHub',
    apiBaseUrl: 'https://api.github.com',
    webBaseUrl: 'https://github.com',
  },
  gitee: {
    label: 'Gitee',
    apiBaseUrl: 'https://gitee.com/api/v5',
    webBaseUrl: 'https://gitee.com',
  },
}

const providerOptions = [
  { label: 'GitLab', value: 'gitlab' },
  { label: 'GitHub', value: 'github' },
  { label: 'Gitee', value: 'gitee' },
]

const branchModeOptions = [
  { label: '仅目标分支', value: 'TARGET_ONLY' },
  { label: '源 + 目标', value: 'SOURCE_AND_TARGET' },
]

const dimensionFields = [
  { key: 'compliance', label: '规范合规' },
  { key: 'correctness', label: '逻辑正确' },
  { key: 'dataIntegrity', label: '数据完整' },
  { key: 'performance', label: '性能风险' },
  { key: 'maintainability', label: '可维护性' },
]

const errorSeverityOptions = [
  { value: 'P0', label: '核心故障', type: 'danger' },
  { value: 'P1', label: '高风险错误', type: 'danger' },
  { value: 'P2', label: '普通错误', type: 'warning' },
  { value: 'P3', label: '低风险聚合', type: 'info' },
]

const errorRouteChannelOptions = [
  { label: '关键通道', value: 'critical' },
  { label: '默认通道', value: 'default' },
]

const errorRoutePriorityOptions = [
  { label: '紧急', value: 'urgent' },
  { label: '普通', value: 'normal' },
  { label: '低优先级', value: 'low' },
  { label: '抑制', value: 'suppress' },
]

const retryBackoffOptions = [0, 5, 10, 30, 60, 120, 300]

const argusExample = {
  basePackages: '["com.lnzz.argus"]',
  moduleSourceRoots: '["argus-common/src/main/java","argus-server/src/main/java"]',
  packageModuleMappings:
    '[{"packagePrefix":"com.lnzz.argus.common","sourceRoot":"argus-common/src/main/java"},{"packagePrefix":"com.lnzz.argus.review","sourceRoot":"argus-server/src/main/java"},{"packagePrefix":"com.lnzz.argus.scm","sourceRoot":"argus-server/src/main/java"},{"packagePrefix":"com.lnzz.argus.notification","sourceRoot":"argus-server/src/main/java"},{"packagePrefix":"com.lnzz.argus.config","sourceRoot":"argus-server/src/main/java"}]',
  maxRelatedClasses: 5,
  maxContextTokens: 12000,
  reviewParallelism: 3,
}

const configs = ref([])
const projectMappings = ref([])
const mappingDrafts = ref([])
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const drawerVisible = ref(false)
const drawerMode = ref('create')
const editingId = ref(null)
const activeTab = ref('basic')
const advancedJsonEditing = ref(false)
const advancedReviewConfigJson = ref('')
const detailVisible = ref(false)
const detailConfig = ref(null)
const formRef = ref()
const router = useRouter()
const mappingEdits = reactive({})

const filters = reactive({
  provider: '',
  enabled: '',
  keyword: '',
})

const form = reactive(createEmptyForm())

const enabledCount = computed(() => configs.value.filter((item) => item.enabled).length)
const triggerConfiguredCount = computed(() => configs.value.filter((item) => hasTriggerConfig(item)).length)
const notifyEnabledCount = computed(() => configs.value.filter((item) => notifyState(item).status === 'ready').length)
const linkedAppCount = computed(() => new Set(projectMappings.value.map((item) => item.appName).filter(Boolean)).size)

const canEditMappings = computed(() => drawerMode.value === 'edit' && form.projectId && form.scmProvider)
const moduleSourceRootOptions = computed(() => parseJsonArray(form.moduleSourceRoots))

const currentMappings = computed(() => {
  if (!canEditMappings.value) return []
  const matched = projectMappings.value
    .filter((item) => isMappingForConfig(item, form))
    .map((item) => mappingEdits[item.id] || { ...item, __editing: false })
  return [...mappingDrafts.value, ...matched]
})

const filteredConfigs = computed(() => {
  return configs.value.filter((item) => {
    if (filters.provider && item.scmProvider !== filters.provider) return false
    if (filters.enabled === 'enabled' && !item.enabled) return false
    if (filters.enabled === 'disabled' && item.enabled) return false
    if (!filters.keyword) return true
    const keyword = filters.keyword.toLowerCase()
    return [
      item.projectName,
      item.repoOwner,
      item.repoName,
      item.apiBaseUrl,
      item.webBaseUrl,
      item.description,
    ].some((value) => String(value || '').toLowerCase().includes(keyword))
  })
})

const webhookUrl = computed(() => webhookUrlFor(form.scmProvider))

const scoreWeightWarning = computed(() => {
  const scoring = form.reviewConfig.scoring
  return Math.abs(Number(scoring.aiWeight || 0) + Number(scoring.ruleWeight || 0) - 1) > 0.001
})

const dimensionWeightWarning = computed(() => {
  const dims = form.reviewConfig.scoring.dimensions
  const total = Object.values(dims).reduce((sum, value) => sum + Number(value || 0), 0)
  return total !== 100
})

watch(
  () => form.reviewConfig,
  () => {
    if (!advancedJsonEditing.value) {
      advancedReviewConfigJson.value = JSON.stringify(form.reviewConfig, null, 2)
    }
  },
  { deep: true },
)

function defaultReviewConfig() {
  return {
    trigger: {
      enabled: true,
      eventTypes: ['opened', 'update', 'synchronize', 'reopened'],
      branchMode: 'TARGET_ONLY',
      targetBranches: ['test'],
      sourceBranches: [],
    },
    vector: {
      enabled: true,
      reviewSearchTopk: 5,
      errorSearchTopk: 5,
      minSimilarity: 0.7,
      knowledgeMinScore: 0.7,
      embeddingTimeoutSec: 30,
    },
    profile: {
      injectEnabled: false,
      lookbackDays: 30,
      clusterTopk: 10,
      injectTopk: 3,
      recentReviewCount: 5,
      scoreTrendCount: 10,
    },
    fileFilter: {
      maxDiffLinesPerFile: 500,
      maxTotalDiffLines: 3000,
      maxReviewFiles: 15,
      excludeFilePatterns: ['**/package-lock.json', '**/yarn.lock', '**/pnpm-lock.yaml', '**/*.min.js', '**/*.min.css'],
      binaryExtensions: ['.jar', '.war', '.png', '.jpg', '.gif', '.pdf', '.doc', '.docx', '.xlsx'],
    },
    token: {
      maxContextTokens: 16000,
      templateReserveTokens: 2000,
      relatedClassTokens: 1000,
      newFilePenalty: 0.8,
      coreModuleBonus: 1.2,
      minTokenPerFile: 800,
    },
    async: {
      scoreTimeoutSec: 120,
      aiTimeoutSec: 180,
      threadPoolSize: 4,
      progressCommentEnabled: true,
      scoreRetryMax: 2,
      scoreRetryDelayMs: 5000,
    },
    scoring: {
      aiWeight: 0.6,
      ruleWeight: 0.4,
      criticalDeduction: 20,
      majorDeduction: 10,
      minorDeduction: 3,
      suggestionDeduction: 0,
      blockThreshold: 60,
      dimensions: {
        compliance: 25,
        correctness: 25,
        dataIntegrity: 20,
        performance: 15,
        maintainability: 15,
      },
    },
    notification: {
      scoreAlertThreshold: 60,
      scoreAlertChannels: ['wechat'],
      wechatNotifyEnabled: true,
      retry: {
        maxRetries: 3,
        backoffSeconds: [30, 120, 300],
        timeoutSec: 600,
      },
      errorAlertRoutes: {
        P0: { enabled: true, channel: 'critical', priority: 'urgent' },
        P1: { enabled: true, channel: 'critical', priority: 'urgent' },
        P2: { enabled: true, channel: 'default', priority: 'normal' },
        P3: { enabled: false, channel: 'default', priority: 'low' },
      },
    },
  }
}

function createEmptyForm() {
  return {
    scmProvider: 'gitlab',
    projectId: null,
    projectName: '',
    repoOwner: '',
    repoName: '',
    apiBaseUrl: '',
    webBaseUrl: '',
    accessToken: '',
    webhookSecret: '',
    basePackages: '',
    moduleSourceRoots: '',
    packageModuleMappings: '',
    maxRelatedClasses: 5,
    maxContextTokens: 12000,
    reviewParallelism: 3,
    enabled: true,
    description: '',
    wechatNotifyEnabled: true,
    wechatNotifyWebhook: '',
    existingWechatWebhookConfigured: false,
    reviewConfig: defaultReviewConfig(),
  }
}

function resetForm() {
  Object.assign(form, createEmptyForm())
  advancedJsonEditing.value = false
  advancedReviewConfigJson.value = JSON.stringify(form.reviewConfig, null, 2)
}

function clearMappingEdits() {
  Object.keys(mappingEdits).forEach((key) => delete mappingEdits[key])
}

function openCreateDrawer() {
  drawerMode.value = 'create'
  editingId.value = null
  activeTab.value = 'basic'
  mappingDrafts.value = []
  clearMappingEdits()
  resetForm()
  drawerVisible.value = true
}

function openEditDrawer(item) {
  drawerMode.value = 'edit'
  editingId.value = item.id
  activeTab.value = 'basic'
  mappingDrafts.value = []
  clearMappingEdits()
  assignFormFromConfig(item)
  drawerVisible.value = true
}

function openDetailDrawer(item) {
  detailConfig.value = item
  detailVisible.value = true
}

function assignFormFromConfig(item) {
  resetForm()
  Object.assign(form, {
    scmProvider: item.scmProvider || 'gitlab',
    projectId: item.projectId ?? null,
    projectName: item.projectName || '',
    repoOwner: item.repoOwner || '',
    repoName: item.repoName || '',
    apiBaseUrl: item.apiBaseUrl || '',
    webBaseUrl: item.webBaseUrl || '',
    accessToken: '',
    webhookSecret: '',
    basePackages: item.basePackages || '',
    moduleSourceRoots: item.moduleSourceRoots || '',
    packageModuleMappings: item.packageModuleMappings || '',
    maxRelatedClasses: item.maxRelatedClasses || 5,
    maxContextTokens: item.maxContextTokens || 12000,
    reviewParallelism: item.reviewParallelism || 3,
    enabled: item.enabled !== false,
    description: item.description || '',
    wechatNotifyEnabled: item.wechatNotifyEnabled == null ? true : Number(item.wechatNotifyEnabled) === 1,
    wechatNotifyWebhook: '',
    existingWechatWebhookConfigured: Boolean(item.wechatNotifyWebhook),
    reviewConfig: parseReviewConfig(item.reviewConfig),
  })
  advancedReviewConfigJson.value = JSON.stringify(form.reviewConfig, null, 2)
}

function parseReviewConfig(raw) {
  const defaults = defaultReviewConfig()
  if (!raw) return defaults
  try {
    return deepMerge(defaults, JSON.parse(raw))
  } catch {
    return defaults
  }
}

function deepMerge(base, override) {
  const result = Array.isArray(base) ? [...base] : { ...base }
  if (!override || typeof override !== 'object') return result
  Object.keys(override).forEach((key) => {
    const nextValue = override[key]
    if (Array.isArray(nextValue)) {
      result[key] = [...nextValue]
    } else if (nextValue && typeof nextValue === 'object' && !Array.isArray(result[key])) {
      result[key] = deepMerge(result[key] || {}, nextValue)
    } else if (nextValue !== null && nextValue !== undefined) {
      result[key] = nextValue
    }
  })
  return result
}

function normalizePayload() {
  const reviewConfig = normalizeReviewConfigForSubmit(advancedJsonEditing.value ? parseAdvancedJson() : form.reviewConfig)
  return {
    scmProvider: form.scmProvider,
    projectId: form.projectId === '' ? null : form.projectId,
    projectName: form.projectName || null,
    repoOwner: form.repoOwner || null,
    repoName: form.repoName || null,
    apiBaseUrl: form.apiBaseUrl || null,
    webBaseUrl: form.webBaseUrl || null,
    accessToken: form.accessToken || null,
    webhookSecret: form.webhookSecret || null,
    basePackages: form.basePackages || null,
    moduleSourceRoots: form.moduleSourceRoots || null,
    packageModuleMappings: form.packageModuleMappings || null,
    maxRelatedClasses: normalizeNumber(form.maxRelatedClasses),
    maxContextTokens: normalizeNumber(form.maxContextTokens),
    reviewParallelism: normalizeNumber(form.reviewParallelism),
    enabled: Boolean(form.enabled),
    description: form.description || null,
    wechatNotifyEnabled: form.wechatNotifyEnabled ? 1 : 0,
    wechatNotifyWebhook: form.wechatNotifyWebhook || null,
    reviewConfig: JSON.stringify(reviewConfig),
  }
}

function normalizeReviewConfigForSubmit(config) {
  const normalized = deepMerge(defaultReviewConfig(), config || {})
  const retry = normalized.notification.retry || {}
  retry.maxRetries = clampInteger(retry.maxRetries, 0, 10, 3)
  retry.timeoutSec = clampInteger(retry.timeoutSec, 1, 3600, 600)
  retry.backoffSeconds = (Array.isArray(retry.backoffSeconds) ? retry.backoffSeconds : [])
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item) && item >= 0)
    .map((item) => Math.floor(item))
  if (!retry.backoffSeconds.length && retry.maxRetries > 0) {
    retry.backoffSeconds = [30, 120, 300]
  }
  normalized.notification.retry = retry
  return normalized
}

function clampInteger(value, min, max, fallback) {
  const number = Number(value)
  if (!Number.isFinite(number)) return fallback
  return Math.min(max, Math.max(min, Math.floor(number)))
}

function parseAdvancedJson() {
  try {
    return JSON.parse(advancedReviewConfigJson.value)
  } catch {
    throw new Error('高级 JSON 不是合法 JSON，请检查格式')
  }
}

function normalizeNumber(value) {
  if (value === '' || value === null || value === undefined) return null
  return Number(value)
}

function validatePayload(payload) {
  if (!payload.projectId && !(payload.repoOwner && payload.repoName)) {
    throw new Error('请至少填写项目 ID，或同时填写仓库归属和仓库名称')
  }
  const keepsExistingWebhook = drawerMode.value === 'edit' && form.existingWechatWebhookConfigured
  if (payload.wechatNotifyEnabled === 1 && !payload.wechatNotifyWebhook && !keepsExistingWebhook) {
    throw new Error('开启企业微信通知时，必须配置 SCM 企业微信 Webhook')
  }
  if (payload.wechatNotifyWebhook && !isValidWebhook(payload.wechatNotifyWebhook)) {
    throw new Error('企业微信 Webhook 必须是 http:// 或 https:// 开头的完整 URL')
  }
  validateJsonField(payload.basePackages, '基础包列表')
  validateJsonField(payload.moduleSourceRoots, '模块源码根列表')
  validateJsonField(payload.packageModuleMappings, '包到模块映射')

  const parsedReviewConfig = JSON.parse(payload.reviewConfig)
  const trigger = parsedReviewConfig.trigger
  if (trigger.branchMode === 'SOURCE_AND_TARGET' && (!trigger.sourceBranches || !trigger.sourceBranches.length)) {
    throw new Error('分支模式为“源 + 目标”时，源分支不能为空')
  }
  const retry = parsedReviewConfig.notification?.retry || {}
  if (retry.maxRetries > 0 && (!retry.backoffSeconds || !retry.backoffSeconds.length)) {
    throw new Error('通知重试次数大于 0 时，退避秒数不能为空')
  }
}

function isValidWebhook(value) {
  if (!value || !String(value).trim()) return false
  try {
    const url = new URL(String(value).trim())
    return ['http:', 'https:'].includes(url.protocol)
  } catch {
    return false
  }
}

function isMaskedSecret(value) {
  return Boolean(value && String(value).includes('********'))
}

function validateJsonField(value, label) {
  if (!value) return
  try {
    JSON.parse(value)
  } catch {
    throw new Error(`${label} 不是合法 JSON，请检查格式`)
  }
}

function hasTriggerConfig(item) {
  if (!item?.reviewConfig) return false
  try {
    return Boolean(JSON.parse(item.reviewConfig).trigger)
  } catch {
    return false
  }
}

async function loadConfigs() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [scmData, mappingData] = await Promise.all([fetchScmConfigs(), fetchProjectMappings()])
    configs.value = scmData || []
    projectMappings.value = mappingData || []
    clearMappingEdits()
  } catch (error) {
    errorMessage.value = error.message || '加载 SCM 配置失败'
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  submitting.value = true
  try {
    const payload = normalizePayload()
    validatePayload(payload)
    if (drawerMode.value === 'edit') {
      await updateScmConfig(editingId.value, payload)
      ElMessage.success('仓库配置已更新')
    } else {
      await createScmConfig(payload)
      ElMessage.success('仓库配置已创建')
    }
    drawerVisible.value = false
    mappingDrafts.value = []
    await loadConfigs()
  } catch (error) {
    ElMessage.error(error.message || '保存 SCM 配置失败')
  } finally {
    submitting.value = false
  }
}

async function toggleEnabled(row) {
  const nextEnabled = !row.enabled
  try {
    await ElMessageBox.confirm(
      `确认${nextEnabled ? '启用' : '停用'}仓库 ${row.projectName || composeRepoName(row)} 吗？`,
      '状态变更确认',
      { type: 'warning' },
    )
    const payload = {
      ...row,
      enabled: nextEnabled,
      accessToken: null,
      webhookSecret: null,
      wechatNotifyWebhook: null,
    }
    await updateScmConfig(row.id, payload)
    ElMessage.success('状态已更新')
    await loadConfigs()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '状态更新失败')
    }
  }
}

function fillArgusExample() {
  Object.assign(form, argusExample)
}

function composeRepoName(item) {
  if (item.repoOwner && item.repoName) return `${item.repoOwner}/${item.repoName}`
  return item.repoName || item.repoOwner || '-'
}

function providerLabel(provider) {
  return providerDefaults[provider]?.label || provider || '-'
}

function providerTagType(provider) {
  if (provider === 'gitlab') return 'warning'
  if (provider === 'github') return 'info'
  if (provider === 'gitee') return 'danger'
  return ''
}

function triggerSummary(row) {
  const trigger = parseReviewConfig(row.reviewConfig).trigger
  if (!trigger.enabled) return '触发关闭'
  return trigger.branchMode === 'SOURCE_AND_TARGET' ? '源 + 目标分支' : '仅目标分支'
}

function branchSummary(row) {
  const trigger = parseReviewConfig(row.reviewConfig).trigger
  const target = trigger.targetBranches?.join(', ') || '-'
  const source = trigger.branchMode === 'SOURCE_AND_TARGET' ? `；源 ${trigger.sourceBranches?.join(', ') || '-'}` : ''
  return `目标 ${target}${source}`
}

function scoreThreshold(row) {
  return parseReviewConfig(row.reviewConfig).scoring.blockThreshold
}

function maxReviewFiles(row) {
  return parseReviewConfig(row.reviewConfig).fileFilter.maxReviewFiles
}

function isNotifyEnabled(row) {
  return row.wechatNotifyEnabled == null || Number(row.wechatNotifyEnabled) === 1
}

function notifyState(row) {
  if (!isNotifyEnabled(row)) {
    return { status: 'disabled', label: '已关闭', description: 'PR 评审与错误告警均不发送', type: 'info' }
  }
  if (!row.wechatNotifyWebhook) {
    return { status: 'missing', label: '未配置', description: '缺少 SCM Webhook', type: 'warning' }
  }
  if (isMaskedSecret(row.wechatNotifyWebhook)) {
    return { status: 'ready', label: '可发送', description: '已配置 SCM Webhook', type: 'success' }
  }
  if (!isValidWebhook(row.wechatNotifyWebhook)) {
    return { status: 'invalid', label: '配置异常', description: 'Webhook URL 非法', type: 'danger' }
  }
  return { status: 'ready', label: '可发送', description: '使用 SCM Webhook', type: 'success' }
}

function scmKey(provider, projectId) {
  return `${provider || ''}:${projectId || ''}`
}

function configKey(config) {
  return scmKey(config.scmProvider, config.projectId)
}

function mappingKey(mapping) {
  return scmKey(mapping.scmProvider, mapping.scmProjectId)
}

function isMappingForConfig(mapping, config) {
  return mappingKey(mapping) === configKey(config)
}

function mappingsForConfig(config) {
  if (!config?.projectId) return []
  return projectMappings.value.filter((item) => isMappingForConfig(item, config))
}

function sourceHealth(config) {
  const mappings = mappingsForConfig(config)
  if (!mappings.length) {
    return { type: 'warning', label: '未绑定', description: '缺少 appName 映射' }
  }
  const incompleteCount = mappings.filter((item) => !item.sourceRoot || !item.basePackage || !item.defaultBranch).length
  if (incompleteCount) {
    return { type: 'warning', label: '部分缺失', description: `${incompleteCount} 个映射缺少源码字段` }
  }
  return { type: 'success', label: '完整', description: `${mappings.length} 个应用可定位` }
}

function mappingHealthText(config) {
  const mappings = mappingsForConfig(config)
  if (!mappings.length) return '错误日志无法联动到该 SCM'
  return `${mappings.length} 个 appName 已联动`
}

function mappingHealth(mapping) {
  if (!mapping.appName) return { type: 'danger', label: '缺 appName' }
  if (!mapping.sourceRoot || !mapping.basePackage || !mapping.defaultBranch) {
    return { type: 'warning', label: '待补齐' }
  }
  return { type: 'success', label: '可定位' }
}

function parseJsonArray(value) {
  try {
    const parsed = JSON.parse(value || '[]')
    if (!Array.isArray(parsed)) return []
    return parsed.map((item) => String(item || '').trim()).filter(Boolean)
  } catch {
    return []
  }
}

function defaultSourceRoot() {
  const roots = moduleSourceRootOptions.value
  if (roots.length === 1) return roots[0]
  if (roots.length > 1) return ''
  return 'src/main/java'
}

function defaultBasePackage() {
  return parseJsonArray(form.basePackages)[0] || ''
}

function addMappingDraft() {
  if (!canEditMappings.value) return
  mappingDrafts.value.unshift({
    __draft: true,
    __editing: true,
    appName: '',
    scmProvider: form.scmProvider,
    scmProjectId: form.projectId,
    sourceRoot: defaultSourceRoot(),
    basePackage: defaultBasePackage(),
    defaultBranch: 'main',
  })
}

function editMapping(row) {
  if (row.__draft) {
    row.__editing = true
    return
  }
  mappingEdits[row.id] = {
    ...row,
    __editing: true,
    __snapshot: { ...row },
  }
}

function cancelMapping(row) {
  if (row.__draft) {
    mappingDrafts.value = mappingDrafts.value.filter((item) => item !== row)
    return
  }
  delete mappingEdits[row.id]
}

async function saveMapping(row) {
  try {
    validateMapping(row)
    const payload = {
      appName: row.appName,
      scmProvider: form.scmProvider,
      scmProjectId: form.projectId,
      sourceRoot: row.sourceRoot,
      basePackage: row.basePackage,
      defaultBranch: row.defaultBranch,
    }
    if (row.__draft) {
      await createProjectMapping(payload)
      ElMessage.success('应用映射已创建')
      mappingDrafts.value = mappingDrafts.value.filter((item) => item !== row)
    } else {
      await updateProjectMapping(row.id, payload)
      ElMessage.success('应用映射已更新')
      delete mappingEdits[row.id]
    }
    await loadConfigs()
  } catch (error) {
    ElMessage.error(error.message || '保存应用映射失败')
  }
}

function validateMapping(row) {
  if (!row.appName) throw new Error('appName 不能为空')
  if (!/^[A-Za-z0-9_.-]{2,100}$/.test(row.appName)) {
    throw new Error('appName 仅支持字母、数字、下划线、中划线和点号，长度 2-100')
  }
  if (!row.sourceRoot) throw new Error('服务源码根不能为空')
  if (row.sourceRoot.startsWith('/')) throw new Error('服务源码根不能以 / 开头')
  if (!row.defaultBranch) throw new Error('默认分支不能为空')
  const duplicate = [...projectMappings.value, ...mappingDrafts.value].some((item) => {
    if (item === row || item.id === row.id) return false
    return item.appName === row.appName
  })
  if (duplicate) throw new Error(`appName ${row.appName} 已存在，一个 appName 只能绑定一个服务源码位置`)
}

async function removeMapping(row) {
  try {
    await ElMessageBox.confirm(
      `删除后，${row.appName} 的错误日志将无法通过当前 SCM 完成源码定位和企业微信告警。确认删除吗？`,
      '删除应用映射',
      { type: 'warning' },
    )
    await deleteProjectMapping(row.id)
    ElMessage.success('应用映射已删除')
    await loadConfigs()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '删除应用映射失败')
    }
  }
}

async function openDataMonitorDrawer(row) {
  if (!currentScmConfigId() || !row.id) {
    ElMessage.warning('请先保存 SCM 配置和应用映射')
    return
  }
  drawerVisible.value = false
  router.push({
    path: '/quality/data-monitor/configs',
    query: {
      scmConfigId: currentScmConfigId(),
      mappingId: row.id,
    },
  })
}

function currentScmConfigId() {
  return editingId.value
}

function webhookUrlFor(provider) {
  return `${window.location.origin}/api/v1/webhook/${provider || 'gitlab'}`
}

function copyWebhook(row) {
  copyText(webhookUrlFor(row.scmProvider))
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

onMounted(async () => {
  resetForm()
  await nextTick()
  loadConfigs()
})
</script>

<style scoped>
.scm-page {
  gap: 18px;
}

.scm-titlebar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 4px 0 2px;
}

.scm-titlebar h2 {
  margin: 0;
  font-size: 28px;
  line-height: 1.15;
}

.scm-titlebar p:last-child {
  margin: 10px 0 0;
  color: var(--muted);
  line-height: 1.7;
}

.title-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.scm-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.scm-stat {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.scm-stat span,
.scm-stat small {
  display: block;
  color: var(--muted);
  font-size: 13px;
}

.scm-stat strong {
  display: block;
  margin: 8px 0 6px;
  font-size: 30px;
  line-height: 1;
}

.scm-workbench {
  padding: 18px;
}

.scm-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.toolbar-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.toolbar-summary {
  color: var(--muted);
  font-size: 13px;
}

.toolbar-summary strong {
  color: var(--text);
}

.scm-alert {
  margin-bottom: 14px;
}

.scm-table {
  width: 100%;
}

.repo-cell {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.repo-cell strong,
.table-main {
  display: block;
  font-weight: 700;
  color: var(--text);
  line-height: 1.45;
}

.repo-cell span,
.table-sub {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.ellipsis-text {
  display: block;
  max-width: 230px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 24px;
}

.muted-inline {
  color: var(--muted);
  font-size: 13px;
}

.mapping-table {
  margin-top: 14px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.scm-form {
  min-height: 100%;
}

.form-section {
  margin-bottom: 22px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
}

.section-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.section-title h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.section-title p {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.dimension-row {
  margin-top: 12px;
}

.json-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.detail-panel h3 {
  margin: 0 0 8px;
  font-size: 20px;
}

.detail-panel p {
  margin: 0 0 18px;
  color: var(--muted);
  line-height: 1.7;
}

.detail-panel dl {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 12px;
  margin: 0;
}

.detail-panel dt {
  color: var(--muted);
  font-weight: 700;
}

.detail-panel dd {
  margin: 0;
  word-break: break-word;
}

@media (max-width: 1100px) {
  .scm-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .scm-titlebar,
  .scm-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
