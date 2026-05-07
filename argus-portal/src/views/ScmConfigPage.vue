<template>
  <section class="page">
    <header class="page-banner">
      <div>
        <p class="eyebrow">Repository Access</p>
        <h1>Git 仓库配置</h1>
        <p class="hero-copy">
          在这里维护 GitLab、GitHub、Gitee 的仓库地址、访问令牌和 Webhook 密钥。
          Argus 会按仓库配置自动匹配 Webhook、拉取代码并回写评审结果。
        </p>
      </div>

      <div class="banner-note">
        <div>
          <span class="metric-label">已接入仓库</span>
          <strong class="metric-value">{{ configs.length }}</strong>
        </div>
        <div>
          <span class="metric-label">启用中</span>
          <strong class="metric-value">{{ enabledCount }}</strong>
        </div>
      </div>
    </header>

    <section class="workspace">
      <div class="panel-card">
        <div class="card-header">
          <div>
            <h2>{{ isEditing ? '编辑仓库配置' : '新增仓库配置' }}</h2>
            <p>支持通过仓库 ID 或 owner/repo 的方式建立映射，并配置项目级代码解析规则。</p>
          </div>
          <button v-if="isEditing" class="ghost-button" type="button" @click="resetForm">
            取消编辑
          </button>
        </div>

        <form class="config-form" @submit.prevent="handleSubmit">
          <div class="field-grid">
            <label class="field">
              <span>SCM 平台</span>
              <select v-model="form.scmProvider" required>
                <option value="gitlab">GitLab</option>
                <option value="github">GitHub</option>
                <option value="gitee">Gitee</option>
              </select>
            </label>

            <label class="field">
              <span>仓库/项目 ID</span>
              <input
                v-model="form.projectId"
                type="number"
                min="0"
                placeholder="例如 123456"
              />
            </label>

            <label class="field">
              <span>仓库归属</span>
              <input v-model.trim="form.repoOwner" type="text" placeholder="例如 lnzz-team" />
            </label>

            <label class="field">
              <span>仓库名称</span>
              <input v-model.trim="form.repoName" type="text" placeholder="例如 argus" />
            </label>

            <label class="field field-span-2">
              <span>仓库显示名称</span>
              <input
                v-model.trim="form.projectName"
                type="text"
                placeholder="例如 lnzz-team/argus"
              />
            </label>

            <label class="field field-span-2">
              <span>API Base URL</span>
              <input
                v-model.trim="form.apiBaseUrl"
                type="text"
                :placeholder="providerPlaceholders[form.scmProvider].apiBaseUrl"
              />
            </label>

            <label class="field field-span-2">
              <span>Web Base URL</span>
              <input
                v-model.trim="form.webBaseUrl"
                type="text"
                :placeholder="providerPlaceholders[form.scmProvider].webBaseUrl"
              />
            </label>

            <label class="field field-span-2">
              <span>访问 Token</span>
              <input
                v-model.trim="form.accessToken"
                type="password"
                :placeholder="isEditing ? '留空表示保留原有 Token' : '输入访问 Token'"
              />
            </label>

            <label class="field field-span-2">
              <span>Webhook Secret</span>
              <input
                v-model.trim="form.webhookSecret"
                type="password"
                :placeholder="isEditing ? '留空表示保留原有 Secret' : '输入 Webhook Secret'"
              />
            </label>

            <label class="field field-span-2">
              <span>备注说明</span>
              <textarea
                v-model.trim="form.description"
                rows="4"
                placeholder="例如：AI 评审主仓库 / 测试环境接入"
              />
            </label>
          </div>

          <div class="subsection">
            <div class="subsection-header">
              <div>
                <h3>代码解析策略</h3>
                <p>用于适配不同项目的包名、模块结构和评审性能，不再在代码里写死 Argus 规则。</p>
              </div>
              <button class="ghost-button" type="button" @click="fillArgusExample">
                填入 Argus 示例
              </button>
            </div>

            <div class="field-grid">
              <label class="field field-span-2">
                <span>基础包列表</span>
                <textarea
                  v-model.trim="form.basePackages"
                  rows="3"
                  placeholder='例如：["com.lnzz.argus"]'
                />
              </label>

              <label class="field field-span-2">
                <span>模块源码根列表</span>
                <textarea
                  v-model.trim="form.moduleSourceRoots"
                  rows="4"
                  placeholder='例如：["argus-common/src/main/java","argus-server/src/main/java"]'
                />
              </label>

              <label class="field field-span-2">
                <span>包到模块映射</span>
                <textarea
                  v-model.trim="form.packageModuleMappings"
                  rows="7"
                  placeholder='例如：[{"packagePrefix":"com.demo.common","sourceRoot":"demo-common/src/main/java"}]'
                />
              </label>

              <label class="field">
                <span>最大关联类数</span>
                <input
                  v-model="form.maxRelatedClasses"
                  type="number"
                  min="1"
                  placeholder="默认 5"
                />
              </label>

              <label class="field">
                <span>最大上下文 Token</span>
                <input
                  v-model="form.maxContextTokens"
                  type="number"
                  min="1000"
                  step="1000"
                  placeholder="默认 16000"
                />
              </label>

              <label class="field">
                <span>评审并发度</span>
                <input
                  v-model="form.reviewParallelism"
                  type="number"
                  min="1"
                  placeholder="默认 3"
                />
              </label>

              <div class="field">
                <span>配置建议</span>
                <div class="hint-card">
                  <strong>通用项目建议</strong>
                  <p>先只配基础包和源码根，确认路径解析稳定后，再细化包到模块映射。</p>
                </div>
              </div>
            </div>
          </div>

          <label class="switch">
            <input v-model="form.enabled" type="checkbox" />
            <span>启用该仓库配置</span>
          </label>

          <div v-if="errorMessage" class="status-banner error">{{ errorMessage }}</div>
          <div v-if="successMessage" class="status-banner success">{{ successMessage }}</div>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="submitting">
              {{ submitting ? '提交中...' : isEditing ? '保存修改' : '创建配置' }}
            </button>
            <button class="secondary-button" type="button" :disabled="loading" @click="loadConfigs">
              刷新列表
            </button>
          </div>
        </form>
      </div>

      <div class="panel-card">
        <div class="card-header">
          <div>
            <h2>配置列表</h2>
            <p>点击任意配置可回填到左侧表单继续编辑。</p>
          </div>
        </div>

        <div class="table-wrap">
          <table class="config-table">
            <thead>
              <tr>
                <th>平台</th>
                <th>仓库</th>
                <th>项目 ID</th>
                <th>API 地址</th>
                <th>并发</th>
                <th>Token</th>
                <th>Secret</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody v-if="configs.length">
              <tr v-for="item in configs" :key="item.id">
                <td>
                  <span class="provider-badge" :data-provider="item.scmProvider">
                    {{ item.scmProvider }}
                  </span>
                </td>
                <td>
                  <div class="repo-name">{{ item.projectName || composeRepoName(item) }}</div>
                  <div class="repo-sub">{{ composeRepoName(item) }}</div>
                </td>
                <td>{{ item.projectId || '-' }}</td>
                <td class="api-url">{{ item.apiBaseUrl || '-' }}</td>
                <td>{{ item.reviewParallelism || 3 }}</td>
                <td>{{ item.accessToken || '-' }}</td>
                <td>{{ item.webhookSecret || '-' }}</td>
                <td>
                  <span class="status-pill" :class="item.enabled ? 'on' : 'off'">
                    {{ item.enabled ? '启用' : '停用' }}
                  </span>
                </td>
                <td>
                  <button class="table-link" type="button" @click="editConfig(item)">编辑</button>
                </td>
              </tr>
            </tbody>
            <tbody v-else>
              <tr>
                <td colspan="9">
                  <div class="empty-state">
                    <p>{{ loading ? '正在加载配置...' : '还没有仓库配置，先在左侧新增一条。' }}</p>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { createScmConfig, fetchScmConfigs, updateScmConfig } from '../api/scm'

const providerPlaceholders = {
  gitlab: {
    apiBaseUrl: 'https://gitlab.com/api/v4',
    webBaseUrl: 'https://gitlab.com',
  },
  github: {
    apiBaseUrl: 'https://api.github.com',
    webBaseUrl: 'https://github.com',
  },
  gitee: {
    apiBaseUrl: 'https://gitee.com/api/v5',
    webBaseUrl: 'https://gitee.com',
  },
}

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
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const editingId = ref(null)

const form = reactive(createEmptyForm())

const isEditing = computed(() => editingId.value !== null)
const enabledCount = computed(() => configs.value.filter((item) => item.enabled).length)

function createEmptyForm() {
  return {
    scmProvider: 'gitlab',
    projectId: '',
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
  }
}

function assignForm(values) {
  Object.assign(form, createEmptyForm(), values)
}

function composeRepoName(item) {
  if (item.repoOwner && item.repoName) {
    return `${item.repoOwner}/${item.repoName}`
  }
  return item.repoName || item.repoOwner || '-'
}

function normalizePayload() {
  return {
    scmProvider: form.scmProvider,
    projectId: form.projectId === '' ? null : Number(form.projectId),
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
    enabled: !!form.enabled,
    description: form.description || null,
  }
}

function normalizeNumber(value) {
  if (value === '' || value === null || value === undefined) {
    return null
  }
  return Number(value)
}

function resetMessages() {
  errorMessage.value = ''
  successMessage.value = ''
}

function resetForm() {
  editingId.value = null
  assignForm(createEmptyForm())
  resetMessages()
}

function fillArgusExample() {
  assignForm({
    ...form,
    ...argusExample,
  })
}

function editConfig(item) {
  editingId.value = item.id
  assignForm({
    scmProvider: item.scmProvider,
    projectId: item.projectId ?? '',
    projectName: item.projectName ?? '',
    repoOwner: item.repoOwner ?? '',
    repoName: item.repoName ?? '',
    apiBaseUrl: item.apiBaseUrl ?? '',
    webBaseUrl: item.webBaseUrl ?? '',
    accessToken: '',
    webhookSecret: '',
    basePackages: item.basePackages ?? '',
    moduleSourceRoots: item.moduleSourceRoots ?? '',
    packageModuleMappings: item.packageModuleMappings ?? '',
    maxRelatedClasses: item.maxRelatedClasses ?? 5,
    maxContextTokens: item.maxContextTokens ?? 12000,
    reviewParallelism: item.reviewParallelism ?? 3,
    enabled: item.enabled,
    description: item.description ?? '',
  })
  resetMessages()
}

async function loadConfigs() {
  loading.value = true
  resetMessages()
  try {
    configs.value = await fetchScmConfigs()
  } catch (error) {
    errorMessage.value = error.message || '加载 SCM 配置失败'
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  submitting.value = true
  resetMessages()

  try {
    const payload = normalizePayload()
    if (!payload.projectId && !(payload.repoOwner && payload.repoName)) {
      throw new Error('请至少填写仓库/项目 ID，或同时填写仓库归属和仓库名称')
    }
    validateJsonField(payload.basePackages, '基础包列表')
    validateJsonField(payload.moduleSourceRoots, '模块源码根列表')
    validateJsonField(payload.packageModuleMappings, '包到模块映射')

    if (editingId.value) {
      await updateScmConfig(editingId.value, payload)
      successMessage.value = '仓库配置已更新'
    } else {
      await createScmConfig(payload)
      successMessage.value = '仓库配置已创建'
    }

    await loadConfigs()
    resetForm()
  } catch (error) {
    errorMessage.value = error.message || '保存 SCM 配置失败'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadConfigs()
})

function validateJsonField(value, label) {
  if (!value) {
    return
  }
  try {
    JSON.parse(value)
  } catch (error) {
    throw new Error(`${label} 不是合法 JSON，请检查格式`)
  }
}
</script>

<style scoped>
.subsection {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid rgba(197, 211, 232, 0.8);
}

.subsection-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.subsection-header h3 {
  margin: 0 0 6px;
  font-size: 18px;
}

.subsection-header p {
  margin: 0;
  color: #6b778c;
  line-height: 1.6;
}

.hint-card {
  height: 100%;
  padding: 14px 16px;
  border: 1px dashed #c5d3e8;
  border-radius: 12px;
  background: #f8fbff;
}

.hint-card strong {
  display: block;
  margin-bottom: 8px;
}

.hint-card p {
  margin: 0;
  color: #6b778c;
  line-height: 1.6;
}
</style>
