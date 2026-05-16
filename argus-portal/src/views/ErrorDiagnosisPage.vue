<template>
  <section class="page error-page">
    <div class="error-titlebar">
      <div>
        <p class="eyebrow">Runtime Intelligence</p>
        <h2>应用服务错误</h2>
        <p>聚合应用服务与入口层错误日志，展示 AI 分析、源码定位、通知状态与人工处理闭环。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading || statsLoading" @click="loadAll">刷新</el-button>
    </div>

    <div class="error-stats">
      <article v-for="item in statCards" :key="item.key" class="error-stat" :class="item.tone">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </article>
    </div>

    <section class="panel-card error-filter-panel">
      <el-form :inline="true" class="error-filter" @submit.prevent>
        <el-form-item label="应用">
          <el-input v-model.trim="filters.appName" clearable placeholder="appName" style="width: 180px" @keyup.enter="queryFirstPage" />
        </el-form-item>
        <el-form-item label="环境">
          <el-select v-model="filters.environment" clearable placeholder="全部环境" style="width: 140px">
            <el-option label="prod" value="prod" />
            <el-option label="test" value="test" />
            <el-option label="staging" value="staging" />
            <el-option label="dev" value="dev" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-segmented v-model="filters.severity" :options="severityOptions" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 170px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model.trim="filters.keyword" clearable placeholder="错误消息 / 指纹 / 类名 / 接口" style="width: 280px" @keyup.enter="queryFirstPage" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="queryFirstPage">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel-card error-table-panel">
      <div class="section-heading">
        <div>
          <h3>错误事件</h3>
          <p>默认按最近发生时间倒序展示，失败详情、通知异常和 AI 任务错误仅在详情抽屉中展开。</p>
        </div>
        <el-tag effect="light">当前 {{ records.length }} / 共 {{ pagination.total }}</el-tag>
      </div>

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="error-alert" />

      <el-table :data="records" v-loading="loading" border class="error-table" @row-click="openDetail">
        <el-table-column label="等级" width="86" fixed>
          <template #default="{ row }">
            <el-tag :type="severityType(displaySeverity(row))" effect="dark">{{ displaySeverity(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应用 / 环境" min-width="190">
          <template #default="{ row }">
            <div class="cell-main">{{ row.appName || '-' }}</div>
            <div class="cell-sub">{{ row.environment || '-' }} · {{ row.hostName || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="错误" min-width="330">
          <template #default="{ row }">
            <div class="error-type-row">
              <el-tag size="small" effect="plain">{{ row.errorType || 'UNKNOWN' }}</el-tag>
              <span>{{ sourceTypeLabel(row.sourceType) }}</span>
            </div>
            <div class="cell-main clamp">{{ row.errorMessage || '-' }}</div>
            <div class="cell-sub">{{ row.className || row.interfaceRef || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="指纹 / 聚合" min-width="180">
          <template #default="{ row }">
            <button class="fingerprint-button" type="button" @click.stop="copyText(row.errorFingerprint)">
              {{ shortHash(row.errorFingerprint) }}
            </button>
            <div class="cell-sub">累计 {{ row.occurrenceCount || 1 }} 次</div>
          </template>
        </el-table-column>
        <el-table-column label="分析" min-width="160">
          <template #default="{ row }">
            <el-tag :type="analysisState(row).type" effect="light">{{ analysisState(row).label }}</el-tag>
            <div class="cell-sub">{{ row.analysisDecision || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="定位" min-width="230">
          <template #default="{ row }">
            <div class="cell-main">{{ locationText(row) }}</div>
            <div class="cell-sub">{{ row.methodName || row.interfaceRef || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="通知" min-width="110">
          <template #default="{ row }">
            <el-tag :type="row.notified ? 'success' : 'info'" effect="light">{{ row.notified ? '已通知' : '未通知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近发生" min-width="170">
          <template #default="{ row }">
            <div class="cell-main">{{ relativeTime(row.lastOccurredAt || row.occurredAt) }}</div>
            <div class="cell-sub">{{ formatDate(row.lastOccurredAt || row.occurredAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="160">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click.stop="openDetail(row)">详情</el-button>
            <el-button link type="primary" :icon="Refresh" @click.stop="quickRetry(row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !records.length" description="暂无错误日志接入" />

      <div class="error-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pagination.pageNo"
          :page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" size="960px" destroy-on-close class="error-drawer">
      <template #header>
        <div class="drawer-title" v-if="detailEvent">
          <el-tag :type="severityType(displaySeverity(detailEvent))" effect="dark">{{ displaySeverity(detailEvent) }}</el-tag>
          <div>
            <h3>{{ detailEvent.errorType || 'UNKNOWN' }} · {{ detailEvent.appName || '-' }}</h3>
            <p>{{ detailEvent.errorMessage || '暂无错误消息' }}</p>
          </div>
        </div>
        <span v-else>错误详情</span>
      </template>

      <el-skeleton v-if="detailLoading" :rows="10" animated />

      <template v-else-if="detailEvent">
        <div class="drawer-actions">
          <el-button type="primary" :icon="Refresh" :loading="actionSubmitting" @click="confirmAction('retry')">重试分析</el-button>
          <el-button :icon="Bell" :loading="actionSubmitting" @click="confirmAction('notify')">重发通知</el-button>
          <el-dropdown>
            <el-button>人工处理</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openActionDialog('ignore')">忽略</el-dropdown-item>
                <el-dropdown-item @click="openActionDialog('falsePositive')">标记误报</el-dropdown-item>
                <el-dropdown-item @click="openActionDialog('severity')">人工调级</el-dropdown-item>
                <el-dropdown-item @click="openActionDialog('conclusion')">补充结论</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="概览" name="overview">
            <div class="detail-block">
              <h4>事件概览</h4>
              <div class="detail-grid">
                <div><span>应用</span><strong>{{ detailEvent.appName || '-' }}</strong></div>
                <div><span>环境</span><strong>{{ detailEvent.environment || '-' }}</strong></div>
                <div><span>来源</span><strong>{{ sourceTypeLabel(detailEvent.sourceType) }}</strong></div>
                <div><span>主机</span><strong>{{ detailEvent.hostName || '-' }}</strong></div>
                <div><span>处理状态</span><strong>{{ statusLabel(detailEvent.processingStatus) }}</strong></div>
                <div><span>分析决策</span><strong>{{ detailEvent.analysisDecision || '-' }}</strong></div>
                <div><span>聚合次数</span><strong>{{ detailEvent.occurrenceCount || 1 }}</strong></div>
                <div><span>最近发生</span><strong>{{ formatDate(detailEvent.lastOccurredAt || detailEvent.occurredAt) }}</strong></div>
                <div><span>traceId</span><strong>{{ detailEvent.lastTraceId || detailEvent.traceId || '-' }}</strong></div>
                <div><span>业务主键</span><strong>{{ detailEvent.lastBusinessKey || detailEvent.businessKey || '-' }}</strong></div>
              </div>
            </div>

            <div class="detail-block">
              <h4>严重度解释</h4>
              <div class="severity-line">
                <el-tag :type="severityType(detailEvent.initialSeverity)" effect="plain">规则 {{ detailEvent.initialSeverity || '-' }}</el-tag>
                <el-tag :type="severityType(detailEvent.finalSeverity)" effect="plain">最终 {{ detailEvent.finalSeverity || detailEvent.severity || '-' }}</el-tag>
                <el-tag effect="plain">{{ detailEvent.severitySource || 'RULE' }}</el-tag>
                <span>{{ confidenceText(detailEvent.severityConfidence) }}</span>
              </div>
              <p class="detail-copy">{{ detailEvent.severityReason || '暂无严重度说明' }}</p>
            </div>

            <div class="detail-block">
              <h4>异常栈</h4>
              <pre class="code-block">{{ detailEvent.rawStackTrace || '暂无异常栈' }}</pre>
            </div>
          </el-tab-pane>

          <el-tab-pane label="AI 分析" name="analysis">
            <template v-if="detailAnalysis">
              <div class="analysis-grid">
                <section>
                  <h4>根因</h4>
                  <p>{{ detailAnalysis.rootCause || '暂无' }}</p>
                </section>
                <section>
                  <h4>影响范围</h4>
                  <p>{{ detailAnalysis.impactScope || '暂无' }}</p>
                </section>
              </div>
              <div class="detail-block">
                <h4>技术细节</h4>
                <p class="detail-copy">{{ detailAnalysis.technicalDetail || '暂无' }}</p>
              </div>
              <div class="detail-block">
                <h4>修复建议</h4>
                <p class="detail-copy">{{ detailAnalysis.fixDescription || '暂无' }}</p>
                <pre v-if="detailAnalysis.fixCodeExample" class="code-block">{{ detailAnalysis.fixCodeExample }}</pre>
              </div>
              <div class="detail-grid">
                <div><span>最终等级</span><strong>{{ detailAnalysis.finalSeverity || '-' }}</strong></div>
                <div><span>置信度</span><strong>{{ confidenceText(detailAnalysis.confidence) }}</strong></div>
                <div><span>模型</span><strong>{{ detailAnalysis.aiModel || '-' }}</strong></div>
                <div><span>耗时</span><strong>{{ durationText(detailAnalysis.duration) }}</strong></div>
                <div><span>Token</span><strong>{{ detailAnalysis.tokensUsed || '-' }}</strong></div>
                <div><span>来源</span><strong>{{ detailAnalysis.source || '-' }}</strong></div>
              </div>
            </template>
            <el-empty v-else :description="emptyAnalysisText(detailEvent)">
              <el-button type="primary" @click="confirmAction('analyze')">立即分析</el-button>
            </el-empty>
          </el-tab-pane>

          <el-tab-pane label="源码定位" name="source">
            <div class="detail-block">
              <h4>源码定位摘要</h4>
              <div class="detail-grid">
                <div><span>appName</span><strong>{{ detailSource.appName || '-' }}</strong></div>
                <div><span>sourceType</span><strong>{{ detailSource.sourceType || '-' }}</strong></div>
                <div><span>类名</span><strong>{{ detailSource.className || '-' }}</strong></div>
                <div><span>方法</span><strong>{{ detailSource.methodName || '-' }}</strong></div>
                <div><span>文件</span><strong>{{ detailSource.filePath || '-' }}</strong></div>
                <div><span>行号</span><strong>{{ detailSource.lineNumber || '-' }}</strong></div>
                <div><span>接口</span><strong>{{ detailSource.interfaceRef || '-' }}</strong></div>
              </div>
              <el-alert
                v-if="!detailSource.className && !detailSource.filePath"
                type="warning"
                show-icon
                :closable="false"
                title="源码定位信息不足，请检查日志是否包含 className/filePath，或在 SCM 配置页维护应用联动"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="上下文日志" name="context">
            <el-empty v-if="!detailContextLogs.length" description="无上下文日志快照" />
            <el-timeline v-else>
              <el-timeline-item v-for="item in detailContextLogs" :key="item.id" :timestamp="formatDate(item.logTime)">
                <div class="log-card">
                  <div>
                    <el-tag size="small" :type="logLevelType(item.logLevel)">{{ item.logLevel || '-' }}</el-tag>
                    <strong>{{ item.loggerName || '-' }}</strong>
                    <span>{{ item.threadName || '-' }}</span>
                  </div>
                  <p>{{ item.message || '-' }}</p>
                  <button type="button" @click="copyText(item.traceId)">traceId: {{ item.traceId || '-' }}</button>
                </div>
              </el-timeline-item>
            </el-timeline>
          </el-tab-pane>

          <el-tab-pane label="通知记录" name="notifications">
            <el-empty v-if="!detailNotifications.length" description="暂无通知记录" />
            <el-table v-else :data="detailNotifications" border>
              <el-table-column prop="channel" label="渠道" min-width="110" />
              <el-table-column label="状态" min-width="110">
                <template #default="{ row }">
                  <el-tag :type="notificationType(row.status)" effect="light">{{ row.status || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="contentSummary" label="摘要" min-width="260" />
              <el-table-column prop="errorMessage" label="失败/跳过原因" min-width="220" />
              <el-table-column prop="retryCount" label="重试" width="80" />
              <el-table-column label="发送时间" min-width="170">
                <template #default="{ row }">{{ formatDate(row.sentAt || row.createTime) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="分析任务" name="tasks">
            <el-empty v-if="!detailTasks.length" description="暂无分析任务" />
            <el-table v-else :data="detailTasks" border>
              <el-table-column prop="triggerType" label="触发" min-width="130" />
              <el-table-column label="状态" min-width="120">
                <template #default="{ row }">
                  <el-tag :type="taskType(row.status)" effect="light">{{ row.status || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="aiModel" label="模型" min-width="140" />
              <el-table-column label="耗时" min-width="100">
                <template #default="{ row }">{{ durationText(row.durationMs) }}</template>
              </el-table-column>
              <el-table-column prop="errorMessage" label="失败原因" min-width="260" />
              <el-table-column label="开始时间" min-width="170">
                <template #default="{ row }">{{ formatDate(row.startedAt || row.createTime) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="知识命中" name="knowledge">
            <el-empty v-if="!detailKnowledge.length" description="暂无知识命中" />
            <div v-else class="knowledge-list">
              <article v-for="item in detailKnowledge" :key="item.id">
                <div>
                  <strong>{{ item.title || item.errorType || '知识条目' }}</strong>
                  <el-tag size="small" effect="plain">{{ item.status || '-' }}</el-tag>
                </div>
                <p>{{ item.rootCause || item.fixSuggestion || item.solution || '暂无摘要' }}</p>
              </article>
            </div>
          </el-tab-pane>

          <el-tab-pane label="人工处理" name="manual">
            <div class="manual-grid">
              <el-button type="primary" @click="confirmAction('retry')">重试分析</el-button>
              <el-button @click="confirmAction('notify')">重发通知</el-button>
              <el-button @click="openActionDialog('severity')">人工调级</el-button>
              <el-button @click="openActionDialog('conclusion')">补充结论</el-button>
              <el-button type="warning" @click="openActionDialog('ignore')">忽略</el-button>
              <el-button type="danger" @click="openActionDialog('falsePositive')">标记误报</el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <el-dialog v-model="actionDialog.visible" :title="actionTitle" width="560px" destroy-on-close>
      <el-form label-position="top" @submit.prevent>
        <template v-if="actionDialog.type === 'ignore' || actionDialog.type === 'falsePositive'">
          <el-form-item label="操作者">
            <el-input v-model.trim="actionForm.operator" placeholder="system / your-name" />
          </el-form-item>
          <el-form-item label="原因">
            <el-input v-model.trim="actionForm.reason" type="textarea" :rows="4" placeholder="请说明处理原因" />
          </el-form-item>
        </template>
        <template v-else-if="actionDialog.type === 'severity'">
          <el-form-item label="调整等级">
            <el-segmented v-model="actionForm.severity" :options="['P0', 'P1', 'P2', 'P3']" />
          </el-form-item>
          <el-form-item label="调整原因">
            <el-input v-model.trim="actionForm.reason" type="textarea" :rows="4" placeholder="请说明调级依据" />
          </el-form-item>
        </template>
        <template v-else-if="actionDialog.type === 'conclusion'">
          <el-form-item label="根因">
            <el-input v-model.trim="actionForm.rootCause" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="严重度">
            <el-segmented v-model="actionForm.severity" :options="['P0', 'P1', 'P2', 'P3']" />
          </el-form-item>
          <el-form-item label="修复建议">
            <el-input v-model.trim="actionForm.fixDescription" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="预防建议">
            <el-input v-model.trim="actionForm.preventionAdvice" type="textarea" :rows="3" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="actionDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="actionSubmitting" @click="submitAction">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Refresh, View } from '@element-plus/icons-vue'
import {
  adjustSeverity,
  analyzeError,
  fetchErrorDetail,
  fetchErrors,
  fetchErrorStats,
  ignoreError,
  markFalsePositive,
  retryAnalyzeError,
  retryNotifyError,
  saveManualConclusion,
} from '../api/error'

const severityOptions = [
  { label: '全部', value: '' },
  { label: 'P0', value: 'P0' },
  { label: 'P1', value: 'P1' },
  { label: 'P2', value: 'P2' },
  { label: 'P3', value: 'P3' },
]

const statusOptions = [
  { label: '已接收', value: 'RECEIVED' },
  { label: '已解析', value: 'PARSED' },
  { label: '已聚合', value: 'AGGREGATED' },
  { label: '分析中', value: 'ANALYZING' },
  { label: '已分析', value: 'ANALYZED' },
  { label: 'AI 降级', value: 'AI_DEGRADED' },
  { label: '通知失败', value: 'NOTIFY_FAILED' },
  { label: '已忽略', value: 'IGNORED' },
  { label: '误报', value: 'FALSE_POSITIVE' },
]

const loading = ref(false)
const statsLoading = ref(false)
const detailLoading = ref(false)
const actionSubmitting = ref(false)
const errorMessage = ref('')
const records = ref([])
const stats = ref({})
const detailVisible = ref(false)
const activeTab = ref('overview')
const detail = ref(null)

const filters = reactive({
  appName: '',
  environment: '',
  severity: '',
  status: '',
  keyword: '',
})

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})

const actionDialog = reactive({
  visible: false,
  type: '',
})

const actionForm = reactive({
  operator: 'system',
  reason: '',
  severity: 'P2',
  rootCause: '',
  fixDescription: '',
  preventionAdvice: '',
})

const detailEvent = computed(() => detail.value?.event || null)
const detailAnalysis = computed(() => detail.value?.analysis || null)
const detailSource = computed(() => detail.value?.sourceLocation || {})
const detailContextLogs = computed(() => detail.value?.contextLogs || [])
const detailNotifications = computed(() => detail.value?.notifications || [])
const detailTasks = computed(() => detail.value?.analysisTasks || [])
const detailKnowledge = computed(() => detail.value?.knowledgeMatches || [])

const statCards = computed(() => {
  const severityCounts = stats.value.severityCounts || {}
  const high = Number(severityCounts.P0 || 0) + Number(severityCounts.P1 || 0)
  return [
    { key: 'total', label: '总错误', value: stats.value.total || 0, hint: '错误事件总量', tone: '' },
    { key: 'unanalyzed', label: '未分析', value: stats.value.unanalyzed || 0, hint: '等待 AI 或人工处理', tone: stats.value.unanalyzed ? 'warning' : '' },
    { key: 'high', label: 'P0/P1', value: high, hint: '高优先级风险', tone: high ? 'danger' : '' },
    { key: 'ignored', label: '已忽略', value: stats.value.ignored || 0, hint: '人工忽略事件', tone: '' },
    { key: 'falsePositive', label: '误报', value: stats.value.falsePositive || 0, hint: '人工确认误报', tone: '' },
  ]
})

const actionTitle = computed(() => {
  const titles = {
    ignore: '忽略错误事件',
    falsePositive: '标记为误报',
    severity: '人工调整严重度',
    conclusion: '补充人工结论',
  }
  return titles[actionDialog.type] || '人工处理'
})

async function loadAll() {
  await Promise.all([loadStats(), loadErrors()])
}

async function loadStats() {
  statsLoading.value = true
  try {
    stats.value = await fetchErrorStats() || {}
  } catch (error) {
    errorMessage.value = error.message || '加载错误统计失败'
  } finally {
    statsLoading.value = false
  }
}

async function loadErrors() {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await fetchErrors({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      appName: filters.appName || undefined,
      environment: filters.environment || undefined,
      severity: filters.severity || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
    })
    records.value = data?.records || []
    pagination.total = Number(data?.total || 0)
    pagination.pageNo = Number(data?.pageNo || pagination.pageNo)
    pagination.pageSize = Number(data?.pageSize || pagination.pageSize)
  } catch (error) {
    errorMessage.value = error.message || '加载错误事件失败'
  } finally {
    loading.value = false
  }
}

function queryFirstPage() {
  pagination.pageNo = 1
  loadErrors()
}

function resetFilters() {
  Object.assign(filters, {
    appName: '',
    environment: '',
    severity: '',
    status: '',
    keyword: '',
  })
  queryFirstPage()
}

function handlePageChange(page) {
  pagination.pageNo = page
  loadErrors()
}

function handleSizeChange(size) {
  pagination.pageSize = size
  pagination.pageNo = 1
  loadErrors()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  activeTab.value = 'overview'
  detail.value = null
  try {
    detail.value = await fetchErrorDetail(row.id)
  } catch (error) {
    ElMessage.error(error.message || '加载错误详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail() {
  if (!detailEvent.value?.id) return
  detail.value = await fetchErrorDetail(detailEvent.value.id)
}

async function quickRetry(row) {
  try {
    await ElMessageBox.confirm(`确认重试分析错误事件 #${row.id} 吗？`, '重试分析', { type: 'warning' })
    await retryAnalyzeError(row.id)
    ElMessage.success('重试分析任务已提交')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '重试分析失败')
  }
}

async function confirmAction(type) {
  if (!detailEvent.value?.id) return
  const messages = {
    retry: '确认重试分析当前错误事件吗？',
    analyze: '确认立即分析当前错误事件吗？',
    notify: '确认重发当前错误事件通知吗？',
  }
  try {
    await ElMessageBox.confirm(messages[type], '操作确认', { type: 'warning' })
    actionSubmitting.value = true
    if (type === 'retry') await retryAnalyzeError(detailEvent.value.id)
    if (type === 'analyze') await analyzeError(detailEvent.value.id)
    if (type === 'notify') await retryNotifyError(detailEvent.value.id)
    ElMessage.success('操作已提交')
    await afterActionRefresh()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '操作失败')
  } finally {
    actionSubmitting.value = false
  }
}

function openActionDialog(type) {
  actionDialog.type = type
  actionDialog.visible = true
  Object.assign(actionForm, {
    operator: 'system',
    reason: '',
    severity: displaySeverity(detailEvent.value) || 'P2',
    rootCause: detailAnalysis.value?.rootCause || '',
    fixDescription: detailAnalysis.value?.fixDescription || '',
    preventionAdvice: detailAnalysis.value?.preventionAdvice || '',
  })
}

async function submitAction() {
  if (!detailEvent.value?.id) return
  try {
    validateAction()
    actionSubmitting.value = true
    const id = detailEvent.value.id
    if (actionDialog.type === 'ignore') await ignoreError(id, { operator: actionForm.operator, reason: actionForm.reason })
    if (actionDialog.type === 'falsePositive') await markFalsePositive(id, { operator: actionForm.operator, reason: actionForm.reason })
    if (actionDialog.type === 'severity') await adjustSeverity(id, { severity: actionForm.severity, reason: actionForm.reason })
    if (actionDialog.type === 'conclusion') {
      await saveManualConclusion(id, {
        rootCause: actionForm.rootCause,
        severity: actionForm.severity,
        fixDescription: actionForm.fixDescription,
        preventionAdvice: actionForm.preventionAdvice,
      })
    }
    actionDialog.visible = false
    ElMessage.success('人工处理已保存')
    await afterActionRefresh()
  } catch (error) {
    ElMessage.error(error.message || '人工处理失败')
  } finally {
    actionSubmitting.value = false
  }
}

function validateAction() {
  if (['ignore', 'falsePositive'].includes(actionDialog.type) && !actionForm.reason) {
    throw new Error('请填写处理原因')
  }
  if (actionDialog.type === 'severity' && !actionForm.reason) {
    throw new Error('请填写调级原因')
  }
  if (actionDialog.type === 'conclusion' && !actionForm.rootCause && !actionForm.fixDescription) {
    throw new Error('请至少填写根因或修复建议')
  }
}

async function afterActionRefresh() {
  await Promise.all([loadStats(), loadErrors(), refreshDetail()])
}

function displaySeverity(row) {
  return row?.finalSeverity || row?.severity || row?.initialSeverity || 'P3'
}

function severityType(severity) {
  if (severity === 'P0' || severity === 'P1') return 'danger'
  if (severity === 'P2') return 'warning'
  return 'info'
}

function analysisState(row) {
  if (row.processingStatus === 'ANALYZING') return { label: '分析中', type: 'primary' }
  if (row.processingStatus === 'AI_DEGRADED') return { label: 'AI 降级', type: 'warning' }
  if (row.analyzed) return { label: '已分析', type: 'success' }
  if (['AGGREGATE_ONLY', 'IGNORE'].includes(row.analysisDecision)) return { label: '跳过 AI', type: 'info' }
  return { label: '待分析', type: 'warning' }
}

function statusLabel(status) {
  return statusOptions.find((item) => item.value === status)?.label || status || '-'
}

function sourceTypeLabel(value) {
  if (value === 'NGINX') return 'Nginx'
  if (value === 'APP' || value === 'AGENT') return '应用'
  return value || '-'
}

function shortHash(value) {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

function locationText(row) {
  if (row.filePath) {
    const filePath = String(row.filePath)
    return `${filePath}${row.lineNumber && !/:\d+$/.test(filePath) ? `:${row.lineNumber}` : ''}`
  }
  if (row.className) return `${row.className}${row.lineNumber ? `:${row.lineNumber}` : ''}`
  return row.interfaceRef || '-'
}

function emptyAnalysisText(row) {
  if (row.processingStatus === 'ANALYZING') return 'AI 分析任务正在执行'
  if (['AGGREGATE_ONLY', 'IGNORE'].includes(row.analysisDecision)) return '该错误按策略聚合观察，未触发 AI'
  return '尚无 AI 分析结果'
}

function confidenceText(value) {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  if (!Number.isFinite(number)) return String(value)
  return `${Math.round(number * 100)}%`
}

function durationText(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number <= 0) return '-'
  if (number >= 1000) return `${(number / 1000).toFixed(1)}s`
  return `${number}ms`
}

function logLevelType(level) {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  return 'info'
}

function notificationType(status) {
  if (status === 'SENT') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'SKIPPED') return 'info'
  return 'warning'
}

function taskType(status) {
  if (status === 'DONE') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'RUNNING' || status === 'PENDING') return 'primary'
  return 'info'
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function relativeTime(value) {
  if (!value) return '-'
  const time = new Date(value).getTime()
  if (!Number.isFinite(time)) return formatDate(value)
  const diff = Date.now() - time
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  return `${Math.floor(diff / day)} 天前`
}

async function copyText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.error-page {
  gap: 18px;
}

.error-titlebar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 26px 28px;
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  background: var(--surface);
  box-shadow: var(--shadow);
}

.error-titlebar h2 {
  margin: 0;
  font-size: 32px;
}

.error-titlebar p:last-child {
  margin: 12px 0 0;
  color: var(--muted);
  line-height: 1.7;
}

.error-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.error-stat {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.error-stat.danger {
  border-color: rgba(239, 68, 68, 0.3);
}

.error-stat.warning {
  border-color: rgba(245, 158, 11, 0.35);
}

.error-stat span,
.error-stat small {
  display: block;
  color: var(--muted);
}

.error-stat strong {
  display: block;
  margin: 8px 0 6px;
  font-size: 32px;
  line-height: 1;
}

.error-filter-panel {
  padding: 18px 20px 2px;
}

.error-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 6px;
}

.error-table-panel {
  min-width: 0;
}

.error-alert {
  margin-bottom: 14px;
}

.error-table {
  width: 100%;
}

.cell-main {
  font-weight: 700;
  color: var(--text);
}

.cell-sub {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}

.clamp {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.error-type-row,
.severity-line,
.drawer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.error-type-row span {
  color: var(--muted);
  font-size: 12px;
}

.fingerprint-button,
.log-card button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #2563eb;
  font: inherit;
  cursor: pointer;
}

.error-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.drawer-title {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.drawer-title h3 {
  margin: 0;
  font-size: 18px;
}

.drawer-title p {
  margin: 6px 0 0;
  color: var(--muted);
  line-height: 1.5;
}

.drawer-actions {
  justify-content: flex-end;
  margin-bottom: 16px;
}

.detail-tabs {
  min-width: 0;
}

.detail-block {
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
}

.detail-block h4,
.analysis-grid h4 {
  margin: 0 0 12px;
  font-size: 15px;
}

.detail-grid,
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-grid div,
.analysis-grid section {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #f8fafc;
}

.detail-grid span {
  display: block;
  color: var(--muted);
  font-size: 12px;
}

.detail-grid strong {
  display: block;
  margin-top: 6px;
  overflow-wrap: anywhere;
}

.detail-copy,
.analysis-grid p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
  white-space: pre-wrap;
}

.code-block {
  max-height: 360px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
}

.log-card {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
}

.log-card div {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-card p {
  margin: 10px 0;
  color: #334155;
  line-height: 1.6;
  white-space: pre-wrap;
}

.knowledge-list {
  display: grid;
  gap: 12px;
}

.knowledge-list article {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
}

.knowledge-list article div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.knowledge-list p {
  margin: 10px 0 0;
  color: var(--muted);
  line-height: 1.6;
}

.manual-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 1100px) {
  .error-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-grid,
  .analysis-grid,
  .manual-grid {
    grid-template-columns: 1fr;
  }
}
</style>
