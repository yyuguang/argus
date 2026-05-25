<script setup lang="tsx">
import { ContentWrap } from '@/components/ContentWrap'
import { Dialog } from '@/components/Dialog'
import { Search } from '@/components/Search'
import { Table, TableColumn } from '@/components/Table'
import { BaseButton } from '@/components/Button'
import { hasPermi } from '@/components/Permission/src/utils'
import { FormSchema } from '@/components/Form'
import { useTable } from '@/hooks/web/useTable'
import {
  adjustSeverityApi,
  analyzeErrorApi,
  getErrorDetailApi,
  getErrorPageApi,
  getErrorStatsApi,
  ignoreErrorApi,
  markFalsePositiveApi,
  retryAnalyzeErrorApi,
  retryNotifyErrorApi,
  saveManualConclusionApi
} from '@/api/error'
import type {
  ErrorAdjustSeverityPayload,
  ErrorDetailResponse,
  ErrorEventItem,
  ErrorManualActionPayload,
  ErrorManualConclusionPayload,
  ErrorPageParams,
  ErrorStatsItem
} from '@/api/error/types'
import { computed, onMounted, reactive, ref, unref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElAlert,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElSkeleton,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTimeline,
  ElTimelineItem
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Bell, Link, Refresh, View } from '@element-plus/icons-vue'

type ManualActionType = 'ignore' | 'false-positive' | 'severity-update' | 'manual-conclusion'

interface ManualFormModel {
  operator: string
  reason: string
  severity: string
  rootCause: string
  fixDescription: string
  preventionAdvice: string
}

const router = useRouter()

const severityOptions = [
  { label: '全部', value: '' },
  { label: 'P0', value: 'P0' },
  { label: 'P1', value: 'P1' },
  { label: 'P2', value: 'P2' },
  { label: 'P3', value: 'P3' }
]

const environmentOptions = [
  { label: 'prod', value: 'prod' },
  { label: 'test', value: 'test' },
  { label: 'staging', value: 'staging' },
  { label: 'dev', value: 'dev' }
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
  { label: '误报', value: 'FALSE_POSITIVE' }
]

const searchSchema = reactive<FormSchema[]>([
  {
    field: 'appName',
    label: '应用',
    component: 'Input',
    componentProps: {
      placeholder: 'appName'
    }
  },
  {
    field: 'environment',
    label: '环境',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: environmentOptions
    }
  },
  {
    field: 'severity',
    label: '等级',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: severityOptions.filter((item) => item.value)
    }
  },
  {
    field: 'status',
    label: '状态',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: statusOptions
    }
  },
  {
    field: 'keyword',
    label: '关键字',
    component: 'Input',
    componentProps: {
      placeholder: '错误消息 / 指纹 / 类名 / 接口',
      maxlength: 100
    }
  }
])

const searchParams = ref<ErrorPageParams>({})
const statsLoading = ref(false)
const actionLoading = ref(false)
const errorMessage = ref('')
const stats = ref<ErrorStatsItem>({})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ErrorDetailResponse>()
const activeDetailTab = ref('overview')

const manualDialogVisible = ref(false)
const manualDialogType = ref<ManualActionType>('ignore')
const manualFormRef = ref<FormInstance>()
const manualForm = reactive<ManualFormModel>({
  operator: 'system',
  reason: '',
  severity: 'P2',
  rootCause: '',
  fixDescription: '',
  preventionAdvice: ''
})

const manualDialogTitleMap: Record<ManualActionType, string> = {
  ignore: '忽略错误事件',
  'false-positive': '标记为误报',
  'severity-update': '人工调级',
  'manual-conclusion': '补充人工结论'
}

const manualDialogTitle = computed(() => manualDialogTitleMap[manualDialogType.value])

const detailEvent = computed(() => detail.value?.event || null)
const detailAnalysis = computed(() => detail.value?.analysis || null)
const detailSource = computed(() => detail.value?.sourceLocation || {})
const detailContextLogs = computed(() => detail.value?.contextLogs || [])
const detailNotifications = computed(() => detail.value?.notifications || [])
const detailTasks = computed(() => detail.value?.analysisTasks || [])
const detailKnowledge = computed(() => detail.value?.knowledgeMatches || [])

const statsCards = computed(() => {
  const severityCounts = stats.value.severityCounts || {}
  const p0p1 = Number(severityCounts.P0 || 0) + Number(severityCounts.P1 || 0)
  return [
    {
      key: 'total',
      title: '总错误',
      value: stats.value.total || 0,
      hint: '错误事件总量'
    },
    {
      key: 'unanalyzed',
      title: '待分析',
      value: stats.value.unanalyzed || 0,
      hint: '等待 AI 或人工处理'
    },
    {
      key: 'p0p1',
      title: 'P0 / P1',
      value: p0p1,
      hint: '高优先级风险'
    },
    {
      key: 'falsePositive',
      title: '误报',
      value: stats.value.falsePositive || 0,
      hint: '人工确认误报'
    }
  ]
})

const manualRules = reactive<FormRules<ManualFormModel>>({
  reason: [
    {
      validator: (_, value, callback) => {
        if (
          ['ignore', 'false-positive', 'severity-update'].includes(manualDialogType.value) &&
          !String(value || '').trim()
        ) {
          callback(new Error('请填写处理原因'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  rootCause: [
    {
      validator: (_, value, callback) => {
        if (
          manualDialogType.value === 'manual-conclusion' &&
          !String(value || '').trim() &&
          !String(manualForm.fixDescription || '').trim()
        ) {
          callback(new Error('请至少填写根因或修复建议'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
})

const formatDate = (value?: string) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const relativeTime = (value?: string) => {
  if (!value) return '-'
  const time = new Date(value).getTime()
  if (!Number.isFinite(time)) return formatDate(value)
  const diff = Date.now() - time
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.max(1, Math.floor(diff / minute))} 分钟前`
  if (diff < day) return `${Math.max(1, Math.floor(diff / hour))} 小时前`
  return `${Math.max(1, Math.floor(diff / day))} 天前`
}

const severityType = (severity?: string) => {
  if (severity === 'P0' || severity === 'P1') return 'danger'
  if (severity === 'P2') return 'warning'
  return 'info'
}

const displaySeverity = (row?: ErrorEventItem | null) => {
  return row?.finalSeverity || row?.severity || row?.initialSeverity || 'P3'
}

const providerAnalysisState = (row: ErrorEventItem) => {
  if (row.processingStatus === 'ANALYZING') {
    return { label: '分析中', type: 'primary' as const }
  }
  if (row.processingStatus === 'AI_DEGRADED') {
    return { label: 'AI 降级', type: 'warning' as const }
  }
  if (row.analyzed) {
    return { label: '已分析', type: 'success' as const }
  }
  if (['AGGREGATE_ONLY', 'IGNORE'].includes(String(row.analysisDecision || ''))) {
    return { label: '跳过 AI', type: 'info' as const }
  }
  return { label: '待分析', type: 'warning' as const }
}

const sourceTypeLabel = (value?: string) => {
  if (value === 'NGINX') return 'Nginx'
  if (value === 'APP' || value === 'AGENT') return '应用'
  return value || '-'
}

const statusLabel = (value?: string) => {
  return statusOptions.find((item) => item.value === value)?.label || value || '-'
}

const shortHash = (value?: string) => {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

const locationText = (row: ErrorEventItem) => {
  if (row.filePath) {
    return `${row.filePath}${row.lineNumber ? `:${row.lineNumber}` : ''}`
  }
  if (row.className) {
    return `${row.className}${row.lineNumber ? `:${row.lineNumber}` : ''}`
  }
  return row.interfaceRef || '-'
}

const confidenceText = (value?: number | string) => {
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  return `${Math.round(num * 100)}%`
}

const durationText = (value?: number) => {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return '-'
  if (num >= 1000) return `${(num / 1000).toFixed(1)}s`
  return `${num}ms`
}

const notificationType = (status?: string) => {
  if (status === 'SENT') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'SKIPPED') return 'info'
  return 'warning'
}

const taskType = (status?: string) => {
  if (status === 'DONE') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'RUNNING' || status === 'PENDING') return 'primary'
  return 'info'
}

const logLevelType = (level?: string) => {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  return 'info'
}

const { tableRegister, tableState, tableMethods } = useTable({
  immediate: false,
  fetchDataApi: async () => {
    const { currentPage, pageSize } = tableState
    const res = await getErrorPageApi({
      pageIndex: unref(currentPage),
      pageSize: unref(pageSize),
      ...unref(searchParams)
    })
    return {
      list: res.data.list || [],
      total: res.data.total || 0
    }
  }
})

const { dataList, loading, total, currentPage, pageSize } = tableState
const { getList } = tableMethods

const loadStats = async () => {
  statsLoading.value = true
  errorMessage.value = ''
  try {
    const res = await getErrorStatsApi()
    stats.value = res.data || {}
  } catch (error: any) {
    errorMessage.value = error?.message || '加载错误统计失败'
  } finally {
    statsLoading.value = false
  }
}

const reloadAll = async () => {
  await Promise.all([loadStats(), getList()])
}

const setSearchParams = (params: Record<string, any>) => {
  searchParams.value = {
    appName: params.appName || undefined,
    environment: params.environment || undefined,
    severity: params.severity || undefined,
    status: params.status || undefined,
    keyword: params.keyword || undefined
  }
  currentPage.value = 1
  getList()
}

const loadDetail = async (id: string | number) => {
  detailLoading.value = true
  try {
    const res = await getErrorDetailApi(id)
    detail.value = res.data
  } finally {
    detailLoading.value = false
  }
}

const openDetail = async (row: ErrorEventItem) => {
  detailVisible.value = true
  activeDetailTab.value = 'overview'
  detail.value = undefined
  try {
    await loadDetail(row.id!)
  } catch (error: any) {
    errorMessage.value = error?.message || '加载错误详情失败'
  }
}

const refreshDetail = async () => {
  if (detailEvent.value?.id) {
    await loadDetail(detailEvent.value.id)
  }
}

const quickRetry = async (row: ErrorEventItem) => {
  actionLoading.value = true
  try {
    await retryAnalyzeErrorApi(row.id!)
    ElMessage.success('重试分析任务已提交')
    await reloadAll()
  } catch (error: any) {
    ElMessage.error(error?.message || '重试分析失败')
  } finally {
    actionLoading.value = false
  }
}

const triggerAnalyze = async (type: 'analyze' | 'retry' | 'notify') => {
  if (!detailEvent.value?.id) return
  actionLoading.value = true
  try {
    if (type === 'analyze') {
      await analyzeErrorApi(detailEvent.value.id)
      ElMessage.success('分析任务已提交')
    } else if (type === 'retry') {
      await retryAnalyzeErrorApi(detailEvent.value.id)
      ElMessage.success('重试分析任务已提交')
    } else {
      await retryNotifyErrorApi(detailEvent.value.id)
      ElMessage.success('重发通知任务已提交')
    }
    await Promise.all([reloadAll(), refreshDetail()])
  } catch (error: any) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    actionLoading.value = false
  }
}

const openManualDialog = (type: ManualActionType) => {
  manualDialogType.value = type
  manualDialogVisible.value = true
  manualForm.operator = 'system'
  manualForm.reason = ''
  manualForm.severity = displaySeverity(detailEvent.value)
  manualForm.rootCause = detailAnalysis.value?.rootCause || ''
  manualForm.fixDescription = detailAnalysis.value?.fixDescription || ''
  manualForm.preventionAdvice = detailAnalysis.value?.preventionAdvice || ''
}

const submitManualAction = async () => {
  const formEl = manualFormRef.value
  if (!formEl || !detailEvent.value?.id) return
  await formEl.validate()

  actionLoading.value = true
  try {
    const eventId = detailEvent.value.id
    if (manualDialogType.value === 'ignore') {
      const payload: ErrorManualActionPayload = {
        operator: manualForm.operator,
        reason: manualForm.reason
      }
      await ignoreErrorApi(eventId, payload)
    } else if (manualDialogType.value === 'false-positive') {
      const payload: ErrorManualActionPayload = {
        operator: manualForm.operator,
        reason: manualForm.reason
      }
      await markFalsePositiveApi(eventId, payload)
    } else if (manualDialogType.value === 'severity-update') {
      const payload: ErrorAdjustSeverityPayload = {
        severity: manualForm.severity,
        reason: manualForm.reason
      }
      await adjustSeverityApi(eventId, payload)
    } else {
      const payload: ErrorManualConclusionPayload = {
        rootCause: manualForm.rootCause,
        severity: manualForm.severity,
        fixDescription: manualForm.fixDescription,
        preventionAdvice: manualForm.preventionAdvice
      }
      await saveManualConclusionApi(eventId, payload)
    }
    ElMessage.success('人工处理已保存')
    manualDialogVisible.value = false
    await Promise.all([reloadAll(), refreshDetail()])
  } catch (error: any) {
    ElMessage.error(error?.message || '人工处理失败')
  } finally {
    actionLoading.value = false
  }
}

const goToAppLinkage = () => {
  router.push({
    path: '/application-governance/app-linkage',
    query: {
      appName: detailSource.value.appName || detailEvent.value?.appName || ''
    }
  })
}

const tableColumns = reactive<TableColumn[]>([
  {
    field: 'severity',
    label: '等级',
    width: 90,
    slots: {
      default: ({ row }: { row: ErrorEventItem }) => (
        <ElTag type={severityType(displaySeverity(row))} effect="dark">
          {displaySeverity(row)}
        </ElTag>
      )
    }
  },
  {
    field: 'appName',
    label: '应用 / 环境',
    minWidth: 190,
    slots: {
      default: ({ row }: { row: ErrorEventItem }) => (
        <div>
          <div class="font-600">{row.appName || '-'}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            {row.environment || '-'} / {row.hostName || '-'}
          </div>
        </div>
      )
    }
  },
  {
    field: 'errorMessage',
    label: '错误',
    minWidth: 320,
    slots: {
      default: ({ row }: { row: ErrorEventItem }) => (
        <div>
          <div class="mb-6px flex items-center gap-8px">
            <ElTag size="small" effect="plain">
              {row.errorType || 'UNKNOWN'}
            </ElTag>
            <span class="text-12px color-[var(--el-text-color-secondary)]">
              {sourceTypeLabel(row.sourceType)}
            </span>
          </div>
          <div class="font-600 clamp-2">{row.errorMessage || '-'}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            {row.className || row.interfaceRef || '-'}
          </div>
        </div>
      )
    }
  },
  {
    field: 'errorFingerprint',
    label: '指纹 / 聚合',
    minWidth: 170,
    slots: {
      default: ({ row }: { row: ErrorEventItem }) => (
        <div>
          <div class="font-600">{shortHash(row.errorFingerprint)}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            累计 {row.occurrenceCount || 1} 次
          </div>
        </div>
      )
    }
  },
  {
    field: 'analysis',
    label: '分析',
    minWidth: 150,
    slots: {
      default: ({ row }: { row: ErrorEventItem }) => {
        const state = providerAnalysisState(row)
        return (
          <div>
            <ElTag type={state.type} effect="light">
              {state.label}
            </ElTag>
            <div class="mt-4px text-12px color-[var(--el-text-color-secondary)]">
              {row.analysisDecision || '-'}
            </div>
          </div>
        )
      }
    }
  },
  {
    field: 'location',
    label: '定位',
    minWidth: 220,
    slots: {
      default: ({ row }: { row: ErrorEventItem }) => (
        <div>
          <div class="font-600">{locationText(row)}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            {row.methodName || row.interfaceRef || '-'}
          </div>
        </div>
      )
    }
  },
  {
    field: 'notified',
    label: '通知',
    width: 100,
    slots: {
      default: ({ row }: { row: ErrorEventItem }) => (
        <ElTag type={row.notified ? 'success' : 'info'} effect="light">
          {row.notified ? '已通知' : '未通知'}
        </ElTag>
      )
    }
  },
  {
    field: 'lastOccurredAt',
    label: '最近发生',
    width: 180,
    slots: {
      default: ({ row }: { row: ErrorEventItem }) => (
        <div>
          <div class="font-600">{relativeTime(row.lastOccurredAt || row.occurredAt)}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            {formatDate(row.lastOccurredAt || row.occurredAt)}
          </div>
        </div>
      )
    }
  },
  {
    field: 'action',
    label: '操作',
    width: 180,
    fixed: 'right',
    slots: {
      default: ({ row }: { row: ErrorEventItem }) => (
        <div class="flex flex-wrap gap-8px">
          {hasPermi('view') ? (
            <BaseButton type="success" icon={View} onClick={() => openDetail(row)}>
              详情
            </BaseButton>
          ) : null}
          {hasPermi('retry-analysis') ? (
            <BaseButton type="primary" icon={Refresh} onClick={() => quickRetry(row)}>
              重试
            </BaseButton>
          ) : null}
        </div>
      )
    }
  }
])

const notificationColumns = reactive<TableColumn[]>([
  {
    field: 'channel',
    label: '渠道',
    minWidth: 110
  },
  {
    field: 'status',
    label: '状态',
    minWidth: 110,
    slots: {
      default: ({ row }: any) => (
        <ElTag type={notificationType(row.status)} effect="light">
          {row.status || '-'}
        </ElTag>
      )
    }
  },
  {
    field: 'contentSummary',
    label: '摘要',
    minWidth: 240
  },
  {
    field: 'errorMessage',
    label: '失败/跳过原因',
    minWidth: 220
  },
  {
    field: 'retryCount',
    label: '重试',
    width: 80
  },
  {
    field: 'sentAt',
    label: '发送时间',
    minWidth: 170,
    slots: {
      default: ({ row }: any) => <>{formatDate(row.sentAt || row.createTime)}</>
    }
  }
])

const taskColumns = reactive<TableColumn[]>([
  {
    field: 'triggerType',
    label: '触发',
    minWidth: 120
  },
  {
    field: 'status',
    label: '状态',
    minWidth: 120,
    slots: {
      default: ({ row }: any) => (
        <ElTag type={taskType(row.status)} effect="light">
          {row.status || '-'}
        </ElTag>
      )
    }
  },
  {
    field: 'aiModel',
    label: '模型',
    minWidth: 140
  },
  {
    field: 'durationMs',
    label: '耗时',
    minWidth: 100,
    slots: {
      default: ({ row }: any) => <>{durationText(row.durationMs)}</>
    }
  },
  {
    field: 'errorMessage',
    label: '失败原因',
    minWidth: 220
  },
  {
    field: 'startedAt',
    label: '开始时间',
    minWidth: 170,
    slots: {
      default: ({ row }: any) => <>{formatDate(row.startedAt || row.createTime)}</>
    }
  }
])

onMounted(() => {
  reloadAll()
})
</script>

<template>
  <ContentWrap>
    <Search :schema="searchSchema" @reset="setSearchParams" @search="setSearchParams" />

    <div class="stats-grid">
      <div v-for="item in statsCards" :key="item.key" class="stats-card">
        <div class="stats-title">{{ item.title }}</div>
        <div class="stats-value">{{ item.value }}</div>
        <div class="stats-hint">{{ item.hint }}</div>
      </div>
    </div>

    <div class="mb-10px flex gap-10px">
      <BaseButton :icon="Refresh" :loading="loading || statsLoading" @click="reloadAll">
        刷新
      </BaseButton>
    </div>

    <ElAlert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="mb-12px"
    />

    <Table
      :columns="tableColumns"
      :data="dataList"
      :loading="loading"
      :pagination="{ total }"
      v-model:pageSize="pageSize"
      v-model:currentPage="currentPage"
      @register="tableRegister"
    />
  </ContentWrap>

  <ElDrawer v-model="detailVisible" title="错误详情" size="980px">
    <ElSkeleton v-if="detailLoading" :rows="10" animated />

    <template v-else-if="detailEvent">
      <div class="drawer-head">
        <ElTag :type="severityType(displaySeverity(detailEvent))" effect="dark">
          {{ displaySeverity(detailEvent) }}
        </ElTag>
        <div class="drawer-head-copy">
          <div class="drawer-head-title">
            {{ detailEvent.errorType || 'UNKNOWN' }} · {{ detailEvent.appName || '-' }}
          </div>
          <div class="drawer-head-desc">{{ detailEvent.errorMessage || '-' }}</div>
        </div>
      </div>

      <div class="mb-12px flex flex-wrap gap-10px">
        <BaseButton
          v-if="hasPermi('retry-analysis')"
          type="primary"
          :icon="Refresh"
          :loading="actionLoading"
          @click="triggerAnalyze('retry')"
        >
          重试分析
        </BaseButton>
        <BaseButton
          v-if="hasPermi('resend-notify')"
          :icon="Bell"
          :loading="actionLoading"
          @click="triggerAnalyze('notify')"
        >
          重发通知
        </BaseButton>
        <BaseButton
          v-if="hasPermi('ignore')"
          :loading="actionLoading"
          @click="openManualDialog('ignore')"
        >
          忽略
        </BaseButton>
        <BaseButton
          v-if="hasPermi('false-positive')"
          type="danger"
          :loading="actionLoading"
          @click="openManualDialog('false-positive')"
        >
          标记误报
        </BaseButton>
        <BaseButton
          v-if="hasPermi('severity-update')"
          type="warning"
          :loading="actionLoading"
          @click="openManualDialog('severity-update')"
        >
          人工调级
        </BaseButton>
        <BaseButton
          v-if="hasPermi('manual-conclusion')"
          :loading="actionLoading"
          @click="openManualDialog('manual-conclusion')"
        >
          补充结论
        </BaseButton>
      </div>

      <ElTabs v-model="activeDetailTab">
        <ElTabPane label="概览" name="overview">
          <ElDescriptions :column="2" border>
            <ElDescriptionsItem label="应用">{{ detailEvent.appName || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="环境">{{
              detailEvent.environment || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="来源">{{
              sourceTypeLabel(detailEvent.sourceType)
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="主机">{{ detailEvent.hostName || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="处理状态">{{
              statusLabel(detailEvent.processingStatus)
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="分析决策">{{
              detailEvent.analysisDecision || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="聚合次数">
              {{ detailEvent.occurrenceCount || 1 }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="最近发生">
              {{ formatDate(detailEvent.lastOccurredAt || detailEvent.occurredAt) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="traceId">
              {{ detailEvent.lastTraceId || detailEvent.traceId || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="业务主键">
              {{ detailEvent.lastBusinessKey || detailEvent.businessKey || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="严重度说明" :span="2">
              {{ detailEvent.severityReason || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="异常栈" :span="2">
              <pre class="detail-code">{{ detailEvent.rawStackTrace || '暂无异常栈' }}</pre>
            </ElDescriptionsItem>
          </ElDescriptions>
        </ElTabPane>

        <ElTabPane label="AI 分析" name="analysis">
          <template v-if="detailAnalysis">
            <ElDescriptions :column="2" border>
              <ElDescriptionsItem label="根因" :span="2">
                {{ detailAnalysis.rootCause || '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="影响范围" :span="2">
                {{ detailAnalysis.impactScope || '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="技术细节" :span="2">
                {{ detailAnalysis.technicalDetail || '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="修复建议" :span="2">
                {{ detailAnalysis.fixDescription || '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="预防建议" :span="2">
                {{ detailAnalysis.preventionAdvice || '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="最终等级">
                {{ detailAnalysis.finalSeverity || '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="置信度">
                {{ confidenceText(detailAnalysis.confidence) }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="模型">
                {{ detailAnalysis.aiModel || '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="耗时">
                {{ durationText(detailAnalysis.duration) }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="Token">
                {{ detailAnalysis.tokensUsed ?? '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="来源">
                {{ detailAnalysis.source || '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem v-if="detailAnalysis.fixCodeExample" label="代码示例" :span="2">
                <pre class="detail-code">{{ detailAnalysis.fixCodeExample }}</pre>
              </ElDescriptionsItem>
            </ElDescriptions>
          </template>
          <ElEmpty v-else description="尚无 AI 分析结果">
            <BaseButton type="primary" @click="triggerAnalyze('analyze')">立即分析</BaseButton>
          </ElEmpty>
        </ElTabPane>

        <ElTabPane label="源码定位" name="source">
          <ElDescriptions :column="2" border>
            <ElDescriptionsItem label="appName">{{
              detailSource.appName || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="sourceType">{{
              detailSource.sourceType || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="类名">{{
              detailSource.className || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="方法">{{
              detailSource.methodName || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="文件">{{ detailSource.filePath || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="行号">{{
              detailSource.lineNumber || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="接口" :span="2">{{
              detailSource.interfaceRef || '-'
            }}</ElDescriptionsItem>
          </ElDescriptions>

          <ElAlert
            v-if="!detailSource.className && !detailSource.filePath"
            title="源码定位信息不足，请检查日志字段或补齐应用联动配置"
            type="warning"
            show-icon
            :closable="false"
            class="mt-12px"
          />

          <div class="mt-12px">
            <BaseButton :icon="Link" @click="goToAppLinkage">去应用联动配置</BaseButton>
          </div>
        </ElTabPane>

        <ElTabPane label="上下文日志" name="context">
          <ElEmpty v-if="!detailContextLogs.length" description="无上下文日志快照" />
          <ElTimeline v-else>
            <ElTimelineItem
              v-for="item in detailContextLogs"
              :key="item.id"
              :timestamp="formatDate(item.logTime)"
            >
              <div class="timeline-card">
                <div class="mb-6px flex items-center gap-8px">
                  <ElTag size="small" :type="logLevelType(item.logLevel)">
                    {{ item.logLevel || '-' }}
                  </ElTag>
                  <strong>{{ item.loggerName || '-' }}</strong>
                  <span class="text-12px color-[var(--el-text-color-secondary)]">{{
                    item.threadName || '-'
                  }}</span>
                </div>
                <div>{{ item.message || '-' }}</div>
              </div>
            </ElTimelineItem>
          </ElTimeline>
        </ElTabPane>

        <ElTabPane label="通知记录" name="notifications">
          <ElEmpty v-if="!detailNotifications.length" description="暂无通知记录" />
          <Table
            v-else
            :data="detailNotifications"
            :pagination="undefined"
            :columns="notificationColumns"
          />
        </ElTabPane>

        <ElTabPane label="分析任务" name="tasks">
          <ElEmpty v-if="!detailTasks.length" description="暂无分析任务" />
          <Table v-else :data="detailTasks" :pagination="undefined" :columns="taskColumns" />
        </ElTabPane>

        <ElTabPane label="知识命中" name="knowledge">
          <ElEmpty v-if="!detailKnowledge.length" description="暂无知识命中" />
          <div v-else class="knowledge-list">
            <div v-for="item in detailKnowledge" :key="item.id" class="knowledge-card">
              <div class="mb-6px flex items-center gap-8px">
                <strong>{{ item.title || item.errorType || '知识条目' }}</strong>
                <ElTag size="small" effect="plain">{{ item.status || '-' }}</ElTag>
              </div>
              <div class="text-13px color-[var(--el-text-color-secondary)]">
                {{ item.rootCause || item.fixSuggestion || '暂无摘要' }}
              </div>
            </div>
          </div>
        </ElTabPane>

        <ElTabPane label="人工处理" name="manual">
          <div class="manual-grid">
            <BaseButton
              v-if="hasPermi('retry-analysis')"
              type="primary"
              @click="triggerAnalyze('retry')"
            >
              重试分析
            </BaseButton>
            <BaseButton v-if="hasPermi('resend-notify')" @click="triggerAnalyze('notify')">
              重发通知
            </BaseButton>
            <BaseButton
              v-if="hasPermi('severity-update')"
              type="warning"
              @click="openManualDialog('severity-update')"
            >
              人工调级
            </BaseButton>
            <BaseButton
              v-if="hasPermi('manual-conclusion')"
              @click="openManualDialog('manual-conclusion')"
            >
              补充结论
            </BaseButton>
            <BaseButton v-if="hasPermi('ignore')" @click="openManualDialog('ignore')">
              忽略
            </BaseButton>
            <BaseButton
              v-if="hasPermi('false-positive')"
              type="danger"
              @click="openManualDialog('false-positive')"
            >
              标记误报
            </BaseButton>
          </div>
        </ElTabPane>
      </ElTabs>
    </template>

    <ElEmpty v-else description="暂无错误详情" />
  </ElDrawer>

  <Dialog v-model="manualDialogVisible" :title="manualDialogTitle" width="560px">
    <ElForm ref="manualFormRef" :model="manualForm" :rules="manualRules" label-position="top">
      <template v-if="manualDialogType === 'ignore' || manualDialogType === 'false-positive'">
        <ElFormItem label="操作者">
          <ElInput v-model="manualForm.operator" placeholder="system / your-name" />
        </ElFormItem>
        <ElFormItem label="原因" prop="reason">
          <ElInput
            v-model="manualForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请说明处理原因"
          />
        </ElFormItem>
      </template>

      <template v-else-if="manualDialogType === 'severity-update'">
        <ElFormItem label="调整等级">
          <ElSelect v-model="manualForm.severity" style="width: 100%">
            <ElOption
              v-for="item in severityOptions.filter((item) => item.value)"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="调整原因" prop="reason">
          <ElInput
            v-model="manualForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请说明调级依据"
          />
        </ElFormItem>
      </template>

      <template v-else>
        <ElFormItem label="根因" prop="rootCause">
          <ElInput v-model="manualForm.rootCause" type="textarea" :rows="3" />
        </ElFormItem>
        <ElFormItem label="严重度">
          <ElSelect v-model="manualForm.severity" style="width: 100%">
            <ElOption
              v-for="item in severityOptions.filter((item) => item.value)"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="修复建议">
          <ElInput v-model="manualForm.fixDescription" type="textarea" :rows="3" />
        </ElFormItem>
        <ElFormItem label="预防建议">
          <ElInput v-model="manualForm.preventionAdvice" type="textarea" :rows="3" />
        </ElFormItem>
      </template>
    </ElForm>

    <template #footer>
      <BaseButton @click="manualDialogVisible = false">取消</BaseButton>
      <BaseButton type="primary" :loading="actionLoading" @click="submitManualAction">
        提交
      </BaseButton>
    </template>
  </Dialog>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stats-card {
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.stats-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stats-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 600;
}

.stats-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.drawer-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.drawer-head-copy {
  min-width: 0;
}

.drawer-head-title {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.drawer-head-desc {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.detail-code {
  margin: 0;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  white-space: pre-wrap;
  word-break: break-word;
}

.timeline-card,
.knowledge-card {
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.knowledge-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.manual-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
