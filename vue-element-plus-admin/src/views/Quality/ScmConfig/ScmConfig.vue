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
  createCodeIndexScanTaskApi,
  getCodeIndexScanTaskApi,
  getLatestCodeIndexApi
} from '@/api/codeIndex'
import type { CodeIndexScanTask, CodeIndexSummaryItem } from '@/api/codeIndex/types'
import CodeIndexScanTaskDialog from './components/CodeIndexScanTaskDialog.vue'
import { getProjectMappingsApi, getScmConfigPageApi, saveScmConfigApi } from '@/api/scm'
import type {
  NotificationPlatformModel,
  ProjectMappingItem,
  ReviewConfigModel,
  ScmConfigItem
} from '@/api/scm/types'
import { computed, onBeforeUnmount, onMounted, reactive, ref, unref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElAlert,
  ElCheckbox,
  ElCheckboxGroup,
  ElCol,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRow,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTabs,
  ElTag
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Edit, Link, Plus, Refresh, RefreshRight, View } from '@element-plus/icons-vue'

interface ScmConfigFormModel {
  id?: string | number
  scmProvider: string
  projectId?: number
  projectName: string
  repoOwner: string
  repoName: string
  apiBaseUrl: string
  webBaseUrl: string
  accessToken: string
  webhookSecret: string
  basePackages: string
  moduleSourceRoots: string
  packageModuleMappings: string
  maxRelatedClasses: number
  maxContextTokens: number
  reviewParallelism: number
  enabled: boolean
  description: string
  reviewConfigModel: ReviewConfigModel
}

const providerOptions = [
  { label: 'GitLab', value: 'gitlab' },
  { label: 'GitHub', value: 'github' },
  { label: 'Gitee', value: 'gitee' }
]

const providerDefaults = {
  gitlab: {
    apiBaseUrl: 'https://gitlab.com/api/v4',
    webBaseUrl: 'https://gitlab.com'
  },
  github: {
    apiBaseUrl: 'https://api.github.com',
    webBaseUrl: 'https://github.com'
  },
  gitee: {
    apiBaseUrl: 'https://gitee.com/api/v5',
    webBaseUrl: 'https://gitee.com'
  }
}

const statusOptions = [
  { label: '启用', value: true },
  { label: '停用', value: false }
]

const eventTypeOptions = ['opened', 'update', 'synchronize', 'reopened']
const branchModeOptions = [
  { label: '仅目标分支', value: 'TARGET_ONLY' },
  { label: '源 + 目标', value: 'SOURCE_AND_TARGET' }
]

const notificationPlatformOptions = [
  { label: '企业微信', value: 'wechat' },
  { label: '飞书', value: 'feishu' },
  { label: '钉钉', value: 'dingtalk' }
]

type CodeIndexTagType = 'success' | 'warning' | 'info' | 'primary' | 'danger'

const router = useRouter()
const CODE_INDEX_SCAN_POLL_INTERVAL = 2000
const CODE_INDEX_SCAN_MAX_POLL_FAILURES = 3

const providerTagType = (provider?: string) => {
  if (provider === 'gitlab') return 'warning'
  if (provider === 'github') return 'info'
  if (provider === 'gitee') return 'danger'
  return 'primary'
}

const formatDate = (value?: string) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const composeRepo = (row: ScmConfigItem) => {
  if (row.repoOwner && row.repoName) {
    return `${row.repoOwner}/${row.repoName}`
  }
  return row.projectName || row.repoName || '-'
}

const createPlatformConfig = (enabled = false): NotificationPlatformModel => ({
  enabled,
  webhook: ''
})

const createDefaultReviewConfig = (): ReviewConfigModel => ({
  trigger: {
    enabled: true,
    eventTypes: ['opened', 'update', 'synchronize', 'reopened'],
    branchMode: 'TARGET_ONLY',
    targetBranches: ['test'],
    sourceBranches: []
  },
  fileFilter: {
    maxReviewFiles: 15,
    maxDiffLinesPerFile: 500,
    maxTotalDiffLines: 3000,
    excludeFilePatterns: ['**/package-lock.json', '**/yarn.lock', '**/pnpm-lock.yaml'],
    binaryExtensions: ['.jar', '.war', '.png', '.jpg', '.gif', '.pdf']
  },
  scoring: {
    blockThreshold: 60,
    aiWeight: 0.6,
    ruleWeight: 0.4
  },
  notification: {
    scoreAlertThreshold: 60,
    scoreAlertChannels: ['wechat'],
    platforms: {
      wechat: createPlatformConfig(true),
      feishu: createPlatformConfig(false),
      dingtalk: createPlatformConfig(false)
    },
    retry: {
      maxRetries: 3,
      timeoutSec: 600,
      backoffSeconds: [30, 120, 300]
    }
  }
})

const deepMerge = <T extends Record<string, any>>(base: T, override?: Record<string, any>): T => {
  const result = Array.isArray(base) ? ([...base] as any) : ({ ...base } as T)
  if (!override || typeof override !== 'object') {
    return result
  }
  Object.keys(override).forEach((key) => {
    const value = override[key]
    if (Array.isArray(value)) {
      result[key] = [...value]
    } else if (value && typeof value === 'object' && !Array.isArray(result[key])) {
      result[key] = deepMerge(result[key] || {}, value)
    } else if (value !== null && value !== undefined) {
      result[key] = value
    }
  })
  return result
}

const parseReviewConfig = (raw?: string): ReviewConfigModel => {
  if (!raw) {
    return createDefaultReviewConfig()
  }
  try {
    return deepMerge(createDefaultReviewConfig(), JSON.parse(raw))
  } catch {
    return createDefaultReviewConfig()
  }
}

const toEditableReviewConfig = (raw?: string): ReviewConfigModel => {
  const config = parseReviewConfig(raw)
  notificationPlatformOptions.forEach((item) => {
    const current = config.notification.platforms?.[item.value] || createPlatformConfig()
    config.notification.platforms[item.value] = {
      enabled: current.enabled === true,
      webhook: ''
    }
  })
  return config
}

const getEnabledPlatforms = (row: ScmConfigItem) => {
  const platforms = parseReviewConfig(row.reviewConfig).notification?.platforms || {}
  return notificationPlatformOptions.filter((item) => platforms[item.value]?.enabled)
}

const summarizeTrigger = (row: ScmConfigItem) => {
  const config = parseReviewConfig(row.reviewConfig)
  const trigger = config.trigger
  if (!trigger.enabled) {
    return '未启用'
  }
  const branches = trigger.targetBranches?.length ? trigger.targetBranches.join('、') : '未配置分支'
  return `${trigger.branchMode === 'SOURCE_AND_TARGET' ? '源+目标' : '目标'}：${branches}`
}

const notifyText = (row: ScmConfigItem) => {
  const reviewConfig = parseReviewConfig(row.reviewConfig)
  const enabledPlatforms = getEnabledPlatforms(row)
  if (!enabledPlatforms.length) return '未启用平台'
  const reviewPlatforms = reviewConfig.notification?.scoreAlertChannels || []
  const labels = enabledPlatforms.map((item) => item.label).join(' / ')
  if (!reviewPlatforms.length) {
    return `${labels}（评审通知未选平台）`
  }
  return `${labels}（评审:${reviewPlatforms.length}）`
}

const scmKey = (provider?: string, projectId?: string | number | null) =>
  `${provider || ''}:${projectId || ''}`

const projectMappings = ref<ProjectMappingItem[]>([])
const codeIndexByScmConfig = ref<Record<string, CodeIndexSummaryItem | undefined>>({})
const codeIndexScanTaskByScmConfig = ref<Record<string, CodeIndexScanTask | undefined>>({})
const codeIndexLoadingMap = ref<Record<string, boolean>>({})
const codeIndexRefreshingMap = ref<Record<string, boolean>>({})
const codeIndexScanTaskVisible = ref(false)
const activeCodeIndexScanTask = ref<CodeIndexScanTask | undefined>()
const activeCodeIndexScanRow = ref<ScmConfigItem | undefined>()
const codeIndexScanTaskPolling = ref(false)
const codeIndexScanTaskPollFailureCount = ref(0)
let codeIndexScanTaskPollTimer: IntervalHandle | undefined

const scmConfigIdKey = (row?: ScmConfigItem) => {
  if (!row?.id) {
    return ''
  }
  return String(row.id)
}

const mappingsForConfig = (row: ScmConfigItem) => {
  return projectMappings.value.filter(
    (item) => scmKey(item.scmProvider, item.scmProjectId) === scmKey(row.scmProvider, row.projectId)
  )
}

const linkageSummary = (row: ScmConfigItem) => {
  const mappings = mappingsForConfig(row)
  if (!mappings.length) {
    return {
      count: 0,
      description: '错误诊断与数据监控暂无法联动到该仓库'
    }
  }
  const incompleteCount = mappings.filter(
    (item) => !item.sourceRoot || !item.basePackage || !item.defaultBranch
  ).length
  if (incompleteCount > 0) {
    return {
      count: mappings.length,
      description: `${incompleteCount} 个应用缺少源码定位关键信息`
    }
  }
  return {
    count: mappings.length,
    description: `${mappings.length} 个应用已可用于源码定位与数据监控`
  }
}

const resolveDefaultBranch = (row: ScmConfigItem) => {
  return mappingsForConfig(row).find((item) => item.defaultBranch)?.defaultBranch || 'main'
}

const latestCodeIndex = (row?: ScmConfigItem) => {
  const key = scmConfigIdKey(row)
  return key ? codeIndexByScmConfig.value[key] : undefined
}

const latestCodeIndexScanTask = (row?: ScmConfigItem) => {
  const key = scmConfigIdKey(row)
  return key ? codeIndexScanTaskByScmConfig.value[key] : undefined
}

const isRunningCodeIndexScanTask = (task?: CodeIndexScanTask) =>
  task?.taskStatus === 'PENDING' || task?.taskStatus === 'RUNNING'

const isTerminalCodeIndexScanTask = (task?: CodeIndexScanTask) =>
  ['SUCCESS', 'FAILED', 'CANCELED', 'REUSED'].includes(String(task?.taskStatus || ''))

const setCodeIndexLoading = (row: ScmConfigItem, loading: boolean) => {
  const key = scmConfigIdKey(row)
  if (!key) {
    return
  }
  codeIndexLoadingMap.value = {
    ...codeIndexLoadingMap.value,
    [key]: loading
  }
}

const setCodeIndexRefreshing = (row: ScmConfigItem, refreshing: boolean) => {
  const key = scmConfigIdKey(row)
  if (!key) {
    return
  }
  codeIndexRefreshingMap.value = {
    ...codeIndexRefreshingMap.value,
    [key]: refreshing
  }
}

const isCodeIndexRefreshing = (row: ScmConfigItem) => {
  const key = scmConfigIdKey(row)
  return key ? codeIndexRefreshingMap.value[key] === true : false
}

const putCodeIndex = (row: ScmConfigItem, index?: CodeIndexSummaryItem) => {
  const key = scmConfigIdKey(row)
  if (!key) {
    return
  }
  codeIndexByScmConfig.value = {
    ...codeIndexByScmConfig.value,
    [key]: index
  }
}

const putCodeIndexScanTask = (row: ScmConfigItem, task?: CodeIndexScanTask) => {
  const key = scmConfigIdKey(row)
  if (!key) {
    return
  }
  codeIndexScanTaskByScmConfig.value = {
    ...codeIndexScanTaskByScmConfig.value,
    [key]: task
  }
}

const setActiveCodeIndexScanTask = (row: ScmConfigItem, task?: CodeIndexScanTask) => {
  putCodeIndexScanTask(row, task)
  activeCodeIndexScanRow.value = row
  activeCodeIndexScanTask.value = task
}

const stopCodeIndexScanTaskPolling = (resetFailureCount = true) => {
  if (codeIndexScanTaskPollTimer) {
    clearInterval(codeIndexScanTaskPollTimer)
    codeIndexScanTaskPollTimer = undefined
  }
  codeIndexScanTaskPolling.value = false
  if (resetFailureCount) {
    codeIndexScanTaskPollFailureCount.value = 0
  }
}

const handleCodeIndexScanTaskTerminal = async (row: ScmConfigItem, task: CodeIndexScanTask) => {
  stopCodeIndexScanTaskPolling()
  if (task.taskStatus === 'SUCCESS' || task.taskStatus === 'REUSED') {
    await loadLatestCodeIndex(row)
    if (task.taskStatus === 'SUCCESS') {
      ElMessage.success(task.message || '源码索引刷新已完成')
    }
    return
  }
  if (task.taskStatus === 'FAILED') {
    const errorMessage = task.latestErrorMessage || task.message
    ElMessage.error(errorMessage ? `源码索引扫描任务失败：${errorMessage}` : '源码索引扫描任务失败')
  }
}

const pollCodeIndexScanTask = async (row: ScmConfigItem, taskId: CodeIndexScanTask['taskId']) => {
  if (!taskId) {
    stopCodeIndexScanTaskPolling()
    return
  }
  try {
    const res = await getCodeIndexScanTaskApi(taskId)
    const task = res.data
    codeIndexScanTaskPollFailureCount.value = 0
    setActiveCodeIndexScanTask(row, task)
    if (task && isTerminalCodeIndexScanTask(task)) {
      await handleCodeIndexScanTaskTerminal(row, task)
    }
  } catch {
    codeIndexScanTaskPollFailureCount.value += 1
    if (codeIndexScanTaskPollFailureCount.value >= CODE_INDEX_SCAN_MAX_POLL_FAILURES) {
      stopCodeIndexScanTaskPolling(false)
      ElMessage.warning('源码索引任务查询连续失败，请稍后手动刷新')
    }
  }
}

const startCodeIndexScanTaskPolling = (row: ScmConfigItem, task: CodeIndexScanTask) => {
  if (!task.taskId || !isRunningCodeIndexScanTask(task)) {
    return
  }
  stopCodeIndexScanTaskPolling()
  setActiveCodeIndexScanTask(row, task)
  codeIndexScanTaskVisible.value = true
  codeIndexScanTaskPolling.value = true
  codeIndexScanTaskPollTimer = setInterval(() => {
    pollCodeIndexScanTask(row, task.taskId)
  }, CODE_INDEX_SCAN_POLL_INTERVAL)
}

const openCodeIndexScanTaskDialog = (row: ScmConfigItem) => {
  const task = latestCodeIndexScanTask(row)
  if (!task) {
    ElMessage.info('暂无源码索引扫描任务')
    return
  }
  activeCodeIndexScanRow.value = row
  activeCodeIndexScanTask.value = task
  codeIndexScanTaskVisible.value = true
  if (isRunningCodeIndexScanTask(task)) {
    startCodeIndexScanTaskPolling(row, task)
  }
}

const loadLatestCodeIndex = async (row: ScmConfigItem) => {
  if (!row.id) {
    return
  }
  setCodeIndexLoading(row, true)
  try {
    const res = await getLatestCodeIndexApi(row.id, resolveDefaultBranch(row))
    putCodeIndex(row, res.data)
  } catch {
    putCodeIndex(row, undefined)
  } finally {
    setCodeIndexLoading(row, false)
  }
}

const loadLatestCodeIndexes = async (rows: ScmConfigItem[]) => {
  await Promise.allSettled(rows.filter((row) => row.id).map((row) => loadLatestCodeIndex(row)))
}

const codeIndexDisplayStatus = (
  row?: ScmConfigItem
): {
  label: string
  type: CodeIndexTagType
} => {
  if (!row?.id) {
    return {
      label: '未扫描',
      type: 'info'
    }
  }
  const key = scmConfigIdKey(row)
  if (codeIndexLoadingMap.value[key]) {
    return {
      label: 'RUNNING',
      type: 'warning'
    }
  }
  const scanTask = latestCodeIndexScanTask(row)
  if (isRunningCodeIndexScanTask(scanTask)) {
    return {
      label: 'RUNNING',
      type: 'warning'
    }
  }
  if (scanTask?.taskStatus === 'REUSED') {
    return {
      label: 'REUSED',
      type: 'success'
    }
  }
  const index = latestCodeIndex(row)
  if (!index) {
    return {
      label: '未扫描',
      type: 'info'
    }
  }
  if (index.scanStatus === 'FAILED') {
    return {
      label: 'FAILED',
      type: 'danger'
    }
  }
  if (index.scanStatus === 'RUNNING' || index.scanStatus === 'PENDING') {
    return {
      label: 'RUNNING',
      type: 'warning'
    }
  }
  if (index.scanStatus === 'PARTIAL' || Number(index.warningCount || 0) > 0) {
    return {
      label: 'WARNING',
      type: 'warning'
    }
  }
  if (index.scanStatus === 'SUCCESS') {
    return {
      label: 'SUCCESS',
      type: 'success'
    }
  }
  return {
    label: 'WARNING',
    type: 'warning'
  }
}

const shortCommitSha = (value?: string) => {
  return value ? value.slice(0, 8) : '-'
}

const codeIndexSummaryText = (row: ScmConfigItem) => {
  const key = scmConfigIdKey(row)
  if (!key) {
    return '保存配置后可刷新索引'
  }
  if (codeIndexLoadingMap.value[key]) {
    return '正在读取最新索引'
  }
  const scanTask = latestCodeIndexScanTask(row)
  if (isRunningCodeIndexScanTask(scanTask)) {
    const taskName = scanTask?.taskNo || (scanTask?.taskId ? `#${scanTask.taskId}` : '扫描任务')
    return `${taskName} · ${scanTask?.scanStage || 'WAITING'} · ${scanTask?.progressPercent ?? 0}%`
  }
  if (scanTask?.taskStatus === 'REUSED') {
    return `已是最新 · 复用索引 ${scanTask.reusedIndexId || scanTask.resultIndexId || '-'}`
  }
  const index = latestCodeIndex(row)
  if (!index) {
    return '暂无索引记录'
  }
  if (index.scanStatus === 'FAILED') {
    return index.latestErrorMessage || '扫描失败'
  }
  return `${index.branchName || resolveDefaultBranch(row)}@${shortCommitSha(index.commitSha)} · ${
    index.classCount || 0
  } 类 / ${index.javaFileCount || 0} 文件`
}

const codeIndexTimeText = (row: ScmConfigItem) => {
  const index = latestCodeIndex(row)
  return formatDate(index?.finishedAt || index?.updateTime || index?.createTime)
}

const submitCodeIndexScanTask = async (
  row: ScmConfigItem,
  options: {
    scanType: 'FULL' | 'REBUILD'
    forceRebuild: boolean
    reason: string
    runningMessage: string
  }
) => {
  if (!row.id) {
    ElMessage.warning('请先保存 SCM 配置后再刷新索引')
    return
  }
  setCodeIndexRefreshing(row, true)
  try {
    const res = await createCodeIndexScanTaskApi(row.id, {
      branchName: resolveDefaultBranch(row),
      scanType: options.scanType,
      forceRebuild: options.forceRebuild,
      reason: options.reason
    })
    const scanTask = res.data
    setActiveCodeIndexScanTask(row, scanTask)
    if (!scanTask) {
      ElMessage.error('源码索引扫描任务创建失败')
      return
    }
    if (scanTask.taskStatus === 'REUSED') {
      ElMessage.success(scanTask.message || '源码索引已是最新')
      await loadLatestCodeIndex(row)
      return
    }
    if (scanTask.taskStatus === 'FAILED') {
      const errorMessage = scanTask.latestErrorMessage || scanTask.message
      ElMessage.error(
        errorMessage ? `源码索引扫描任务失败：${errorMessage}` : '源码索引扫描任务失败'
      )
      codeIndexScanTaskVisible.value = true
      return
    }
    if (scanTask.taskStatus === 'SUCCESS') {
      ElMessage.success(scanTask.message || '源码索引刷新已完成')
      await loadLatestCodeIndex(row)
      return
    }
    if (isRunningCodeIndexScanTask(scanTask)) {
      ElMessage.success(scanTask.message || options.runningMessage)
      startCodeIndexScanTaskPolling(row, scanTask)
      return
    }
    ElMessage.info(scanTask.message || '源码索引扫描任务已提交')
  } finally {
    setCodeIndexRefreshing(row, false)
  }
}

const refreshCodeIndex = async (row: ScmConfigItem) => {
  await submitCodeIndexScanTask(row, {
    scanType: 'FULL',
    forceRebuild: false,
    reason: 'SCM_CONFIG_MANUAL_REFRESH',
    runningMessage: '源码索引扫描任务已创建'
  })
}

const rebuildCodeIndex = async (row: ScmConfigItem) => {
  try {
    await ElMessageBox.confirm(
      '强制重建会重新解析当前分支源码；新任务成功前，旧成功索引仍继续用于源码定位和评审上下文。',
      '强制重建源码索引',
      {
        type: 'warning',
        confirmButtonText: '强制重建',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  await submitCodeIndexScanTask(row, {
    scanType: 'REBUILD',
    forceRebuild: true,
    reason: 'SCM_CONFIG_FORCE_REBUILD',
    runningMessage: '源码索引强制重建任务已创建'
  })
}

const providerDefault = (key: 'apiBaseUrl' | 'webBaseUrl') => {
  const provider = form.scmProvider as keyof typeof providerDefaults
  return providerDefaults[provider]?.[key] || ''
}

const buildEmptyForm = (): ScmConfigFormModel => ({
  id: undefined,
  scmProvider: 'gitlab',
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
  maxContextTokens: 16000,
  reviewParallelism: 3,
  enabled: true,
  description: '',
  reviewConfigModel: createDefaultReviewConfig()
})

const form = reactive<ScmConfigFormModel>(buildEmptyForm())
const formRef = ref<FormInstance>()
const formVisible = ref(false)
const detailVisible = ref(false)
const currentRow = ref<ScmConfigItem>()
const activeTab = ref('basic')
const saveLoading = ref(false)

const dialogTitle = computed(() => (form.id ? '编辑 SCM 配置' : '新增 SCM 配置'))

const rules = reactive<FormRules<ScmConfigFormModel>>({
  scmProvider: [{ required: true, message: '请选择 SCM 平台', trigger: 'change' }]
})

const searchSchema = reactive<FormSchema[]>([
  {
    field: 'scmProvider',
    label: 'SCM 平台',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: providerOptions
    }
  },
  {
    field: 'enabled',
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
      placeholder: '项目名 / 仓库 / API 地址',
      maxlength: 100
    }
  }
])

const searchParams = ref<Record<string, any>>({})

const { tableRegister, tableState, tableMethods } = useTable({
  immediate: false,
  fetchDataApi: async () => {
    const { pageSize, currentPage } = tableState
    const res = await getScmConfigPageApi({
      pageIndex: unref(currentPage),
      pageSize: unref(pageSize),
      ...unref(searchParams)
    })
    const list = res.data.list || []
    await loadLatestCodeIndexes(list)
    return {
      list,
      total: res.data.total || 0
    }
  }
})
const { dataList, loading, total, currentPage, pageSize } = tableState
const { getList } = tableMethods

const loadProjectMappings = async () => {
  const res = await getProjectMappingsApi()
  projectMappings.value = res.data || []
}

const tableColumns = reactive<TableColumn[]>([
  {
    field: 'index',
    label: '序号',
    type: 'index'
  },
  {
    field: 'projectName',
    label: '仓库',
    minWidth: 260,
    slots: {
      default: ({ row }: { row: ScmConfigItem }) => (
        <div>
          <div class="font-600">{row.projectName || composeRepo(row)}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">{composeRepo(row)}</div>
        </div>
      )
    }
  },
  {
    field: 'scmProvider',
    label: '平台',
    width: 110,
    slots: {
      default: ({ row }: { row: ScmConfigItem }) => (
        <ElTag type={providerTagType(row.scmProvider)} effect="light">
          {row.scmProvider || '-'}
        </ElTag>
      )
    }
  },
  {
    field: 'projectId',
    label: '项目 ID',
    width: 120,
    slots: {
      default: ({ row }: { row: ScmConfigItem }) => <>{row.projectId || '-'}</>
    }
  },
  {
    field: 'linkage',
    label: '应用联动',
    minWidth: 240,
    slots: {
      default: ({ row }: { row: ScmConfigItem }) => (
        <div>
          <div class="font-600">{linkageSummary(row).count} 个应用</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            {linkageSummary(row).description}
          </div>
        </div>
      )
    }
  },
  {
    field: 'codeIndex',
    label: '源码索引',
    minWidth: 220,
    slots: {
      default: ({ row }: { row: ScmConfigItem }) => {
        const display = codeIndexDisplayStatus(row)
        return (
          <div>
            <ElTag type={display.type} effect="light">
              {display.label}
            </ElTag>
            <div class="mt-4px text-12px color-[var(--el-text-color-secondary)]">
              {codeIndexSummaryText(row)}
            </div>
          </div>
        )
      }
    }
  },
  {
    field: 'trigger',
    label: '触发规则',
    minWidth: 220,
    slots: {
      default: ({ row }: { row: ScmConfigItem }) => <>{summarizeTrigger(row)}</>
    }
  },
  {
    field: 'notify',
    label: '通知',
    minWidth: 160,
    slots: {
      default: ({ row }: { row: ScmConfigItem }) => <>{notifyText(row)}</>
    }
  },
  {
    field: 'enabled',
    label: '状态',
    width: 100,
    slots: {
      default: ({ row }: { row: ScmConfigItem }) => (
        <ElTag type={row.enabled === false ? 'danger' : 'success'}>
          {row.enabled === false ? '停用' : '启用'}
        </ElTag>
      )
    }
  },
  {
    field: 'updateTime',
    label: '更新时间',
    width: 180,
    slots: {
      default: ({ row }: { row: ScmConfigItem }) => (
        <>{formatDate(row.updateTime || row.createTime)}</>
      )
    }
  },
  {
    field: 'action',
    label: '操作',
    width: 400,
    fixed: 'right',
    slots: {
      default: ({ row }: { row: ScmConfigItem }) => (
        <div class="flex flex-wrap gap-8px">
          {hasPermi('view') ? (
            <BaseButton icon={Link} onClick={() => goAppLinkage(row)}>
              联动
            </BaseButton>
          ) : null}
          {hasPermi('update') ? (
            <BaseButton
              icon={Refresh}
              loading={isCodeIndexRefreshing(row)}
              onClick={() => refreshCodeIndex(row)}
            >
              刷新索引
            </BaseButton>
          ) : null}
          {hasPermi('update') ? (
            <BaseButton
              icon={RefreshRight}
              loading={isCodeIndexRefreshing(row)}
              onClick={() => rebuildCodeIndex(row)}
            >
              强制重建
            </BaseButton>
          ) : null}
          {hasPermi('view') && latestCodeIndexScanTask(row) ? (
            <BaseButton icon={View} onClick={() => openCodeIndexScanTaskDialog(row)}>
              进度
            </BaseButton>
          ) : null}
          {hasPermi('update') ? (
            <BaseButton type="primary" icon={Edit} onClick={() => openEdit(row)}>
              编辑
            </BaseButton>
          ) : null}
          {hasPermi('view') ? (
            <BaseButton type="success" icon={View} onClick={() => openDetail(row)}>
              详情
            </BaseButton>
          ) : null}
        </div>
      )
    }
  }
])

const setSearchParams = (params: Record<string, any>) => {
  searchParams.value = params
  currentPage.value = 1
  getList()
}

const goAppLinkage = (row?: ScmConfigItem) => {
  router.push({
    path: '/application-governance/app-linkage',
    query: row?.id ? { scmConfigId: row.id } : undefined
  })
}

const resetForm = () => {
  Object.assign(form, buildEmptyForm())
  formRef.value?.clearValidate()
}

const openCreate = () => {
  resetForm()
  activeTab.value = 'basic'
  formVisible.value = true
}

const openEdit = (row: ScmConfigItem) => {
  resetForm()
  Object.assign(form, {
    id: row.id,
    scmProvider: row.scmProvider || 'gitlab',
    projectId: row.projectId ?? undefined,
    projectName: row.projectName || '',
    repoOwner: row.repoOwner || '',
    repoName: row.repoName || '',
    apiBaseUrl: row.apiBaseUrl || '',
    webBaseUrl: row.webBaseUrl || '',
    accessToken: '',
    webhookSecret: '',
    basePackages: row.basePackages || '',
    moduleSourceRoots: row.moduleSourceRoots || '',
    packageModuleMappings: row.packageModuleMappings || '',
    maxRelatedClasses: row.maxRelatedClasses || 5,
    maxContextTokens: row.maxContextTokens || 16000,
    reviewParallelism: row.reviewParallelism || 3,
    enabled: row.enabled !== false,
    description: row.description || '',
    reviewConfigModel: toEditableReviewConfig(row.reviewConfig)
  })
  activeTab.value = 'basic'
  formVisible.value = true
}

const openDetail = (row: ScmConfigItem) => {
  currentRow.value = row
  detailVisible.value = true
}

const parseNumberList = (items: unknown[]) => {
  return items
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item) && item >= 0)
    .map((item) => Math.floor(item))
}

const normalizeReviewConfig = () => {
  const config = deepMerge(createDefaultReviewConfig(), form.reviewConfigModel)
  const normalizedPlatforms: ReviewConfigModel['notification']['platforms'] = {
    wechat: createPlatformConfig(),
    feishu: createPlatformConfig(),
    dingtalk: createPlatformConfig()
  }
  notificationPlatformOptions.forEach((item) => {
    const current = config.notification.platforms?.[item.value] || createPlatformConfig()
    normalizedPlatforms[item.value] = {
      enabled: current.enabled === true,
      webhook: current.webhook || ''
    }
  })
  config.notification.platforms = normalizedPlatforms
  config.notification.scoreAlertChannels = (config.notification.scoreAlertChannels || []).filter(
    (item) =>
      notificationPlatformOptions.some((platform) => platform.value === item) &&
      config.notification.platforms[item]?.enabled
  )
  config.notification.retry.backoffSeconds = parseNumberList(
    config.notification.retry.backoffSeconds || []
  )
  if (
    config.notification.retry.maxRetries > 0 &&
    !config.notification.retry.backoffSeconds.length
  ) {
    config.notification.retry.backoffSeconds = [30, 120, 300]
  }
  return config
}

const validateBusiness = () => {
  if (!form.projectId && !(form.repoOwner && form.repoName)) {
    throw new Error('请填写项目 ID，或同时填写仓库归属和仓库名称')
  }
  if (
    form.reviewConfigModel.trigger.branchMode === 'SOURCE_AND_TARGET' &&
    !form.reviewConfigModel.trigger.sourceBranches.length
  ) {
    throw new Error('分支模式为“源 + 目标”时，源分支不能为空')
  }
}

const buildPayload = (): ScmConfigItem => ({
  id: form.id,
  ...(() => {
    const reviewConfig = normalizeReviewConfig()
    const wechatPlatform = reviewConfig.notification.platforms.wechat || createPlatformConfig(true)
    const feishuPlatform = reviewConfig.notification.platforms.feishu || createPlatformConfig()
    const dingtalkPlatform = reviewConfig.notification.platforms.dingtalk || createPlatformConfig()
    return {
      scmProvider: form.scmProvider,
      projectId: form.projectId ?? null,
      projectName: form.projectName || undefined,
      repoOwner: form.repoOwner || undefined,
      repoName: form.repoName || undefined,
      apiBaseUrl: form.apiBaseUrl || undefined,
      webBaseUrl: form.webBaseUrl || undefined,
      accessToken: form.accessToken || undefined,
      webhookSecret: form.webhookSecret || undefined,
      basePackages: form.basePackages || undefined,
      moduleSourceRoots: form.moduleSourceRoots || undefined,
      packageModuleMappings: form.packageModuleMappings || undefined,
      maxRelatedClasses: form.maxRelatedClasses,
      maxContextTokens: form.maxContextTokens,
      reviewParallelism: form.reviewParallelism,
      enabled: form.enabled,
      description: form.description || undefined,
      wechatNotifyEnabled: wechatPlatform.enabled,
      wechatNotifyWebhook: wechatPlatform.webhook || undefined,
      feishuNotifyEnabled: feishuPlatform.enabled,
      feishuNotifyWebhook: feishuPlatform.webhook || undefined,
      dingtalkNotifyEnabled: dingtalkPlatform.enabled,
      dingtalkNotifyWebhook: dingtalkPlatform.webhook || undefined,
      reviewConfig: JSON.stringify(reviewConfig)
    }
  })()
})

const save = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  try {
    validateBusiness()
  } catch (error: any) {
    ElMessage.warning(error.message)
    return
  }

  saveLoading.value = true
  try {
    const creating = !form.id
    await saveScmConfigApi(buildPayload())
    ElMessage.success(creating ? 'SCM 配置创建成功' : 'SCM 配置更新成功')
    formVisible.value = false
    await loadProjectMappings()
    if (creating) {
      currentPage.value = 1
    }
    getList()
  } finally {
    saveLoading.value = false
  }
}

const refreshAll = async () => {
  await loadProjectMappings()
  await getList()
}

onMounted(async () => {
  await refreshAll()
})

watch(codeIndexScanTaskVisible, (visible) => {
  if (!visible) {
    stopCodeIndexScanTaskPolling()
  }
})

onBeforeUnmount(() => {
  stopCodeIndexScanTaskPolling()
})
</script>

<template>
  <ContentWrap>
    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="mb-12px"
      title="SCM 页面只负责仓库接入、鉴权和评审策略；appName 绑定、错误告警路由和数据监控入口统一转到“应用联动配置”页面。"
    />
    <Search :schema="searchSchema" @reset="setSearchParams" @search="setSearchParams" />
    <div class="mb-10px flex gap-10px">
      <BaseButton v-if="hasPermi('create')" type="primary" :icon="Plus" @click="openCreate">
        新增配置
      </BaseButton>
      <BaseButton :icon="Link" @click="goAppLinkage()">应用联动配置</BaseButton>
      <BaseButton :icon="Refresh" :loading="loading" @click="refreshAll">刷新</BaseButton>
    </div>
    <Table
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :columns="tableColumns"
      :data="dataList"
      :loading="loading"
      :pagination="{ total }"
      @register="tableRegister"
    />
  </ContentWrap>

  <CodeIndexScanTaskDialog
    v-model="codeIndexScanTaskVisible"
    :task="activeCodeIndexScanTask"
    :polling="codeIndexScanTaskPolling"
    :poll-failure-count="codeIndexScanTaskPollFailureCount"
  />

  <Dialog v-model="formVisible" :title="dialogTitle" width="900px" max-height="calc(100vh - 170px)">
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top">
      <ElTabs v-model="activeTab">
        <ElTabPane label="基础信息" name="basic">
          <ElRow :gutter="16">
            <ElCol :span="8">
              <ElFormItem label="SCM 平台" prop="scmProvider">
                <ElSelect v-model="form.scmProvider" class="w-100%">
                  <ElOption
                    v-for="item in providerOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="项目 ID">
                <ElInputNumber
                  v-model="form.projectId"
                  :min="0"
                  controls-position="right"
                  class="w-100%"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="启用状态">
                <ElSwitch v-model="form.enabled" active-text="启用" inactive-text="停用" />
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElRow :gutter="16">
            <ElCol :span="12">
              <ElFormItem label="仓库归属">
                <ElInput
                  v-model.trim="form.repoOwner"
                  maxlength="120"
                  placeholder="例如 lnzz-team"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="仓库名称">
                <ElInput v-model.trim="form.repoName" maxlength="120" placeholder="例如 argus" />
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElFormItem label="仓库显示名称">
            <ElInput
              v-model.trim="form.projectName"
              maxlength="180"
              placeholder="例如 lnzz-team/argus"
            />
          </ElFormItem>
          <ElRow :gutter="16">
            <ElCol :span="12">
              <ElFormItem label="API Base URL">
                <ElInput
                  v-model.trim="form.apiBaseUrl"
                  :placeholder="providerDefault('apiBaseUrl')"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="Web Base URL">
                <ElInput
                  v-model.trim="form.webBaseUrl"
                  :placeholder="providerDefault('webBaseUrl')"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElFormItem label="说明">
            <ElInput v-model.trim="form.description" type="textarea" :rows="3" maxlength="500" />
          </ElFormItem>
        </ElTabPane>

        <ElTabPane label="鉴权通知" name="secret">
          <ElAlert
            v-if="form.id"
            title="编辑时访问 Token、Webhook Secret、各通知平台 Webhook 留空表示保留原值"
            type="info"
            show-icon
            :closable="false"
            class="mb-12px"
          />
          <ElFormItem label="访问 Token">
            <ElInput v-model.trim="form.accessToken" type="password" show-password />
          </ElFormItem>
          <ElFormItem label="Webhook Secret">
            <ElInput v-model.trim="form.webhookSecret" type="password" show-password />
          </ElFormItem>
          <div class="platform-config-list">
            <div
              v-for="item in notificationPlatformOptions"
              :key="item.value"
              class="platform-config-item"
            >
              <ElRow :gutter="16">
                <ElCol :span="8">
                  <ElFormItem :label="`${item.label}通知`">
                    <ElSwitch
                      v-model="form.reviewConfigModel.notification.platforms[item.value].enabled"
                      active-text="开启"
                      inactive-text="关闭"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="16">
                  <ElFormItem :label="`${item.label} Webhook`">
                    <ElInput
                      v-model.trim="
                        form.reviewConfigModel.notification.platforms[item.value].webhook
                      "
                      type="password"
                      show-password
                      :placeholder="form.id ? '留空则保留原值' : '请输入 Webhook 地址'"
                    />
                  </ElFormItem>
                </ElCol>
              </ElRow>
            </div>
          </div>
        </ElTabPane>

        <ElTabPane label="触发与评分" name="review">
          <ElRow :gutter="16">
            <ElCol :span="8">
              <ElFormItem label="启用触发">
                <ElSwitch v-model="form.reviewConfigModel.trigger.enabled" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="16">
              <ElFormItem label="分支模式">
                <ElSelect v-model="form.reviewConfigModel.trigger.branchMode" class="w-100%">
                  <ElOption
                    v-for="item in branchModeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElFormItem label="事件类型">
            <ElCheckboxGroup v-model="form.reviewConfigModel.trigger.eventTypes">
              <ElCheckbox v-for="item in eventTypeOptions" :key="item" :value="item">
                {{ item }}
              </ElCheckbox>
            </ElCheckboxGroup>
          </ElFormItem>
          <ElFormItem label="目标分支">
            <ElSelect
              v-model="form.reviewConfigModel.trigger.targetBranches"
              multiple
              filterable
              allow-create
              default-first-option
              class="w-100%"
              placeholder="例如 test、develop、release/*"
            />
          </ElFormItem>
          <ElFormItem
            v-if="form.reviewConfigModel.trigger.branchMode === 'SOURCE_AND_TARGET'"
            label="源分支"
          >
            <ElSelect
              v-model="form.reviewConfigModel.trigger.sourceBranches"
              multiple
              filterable
              allow-create
              default-first-option
              class="w-100%"
              placeholder="例如 dev、feature/*"
            />
          </ElFormItem>
          <ElRow :gutter="16">
            <ElCol :span="8">
              <ElFormItem label="最大评审文件数">
                <ElInputNumber
                  v-model="form.reviewConfigModel.fileFilter.maxReviewFiles"
                  :min="1"
                  controls-position="right"
                  class="w-100%"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="阻止阈值">
                <ElInputNumber
                  v-model="form.reviewConfigModel.scoring.blockThreshold"
                  :min="0"
                  :max="100"
                  controls-position="right"
                  class="w-100%"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="低分告警阈值">
                <ElInputNumber
                  v-model="form.reviewConfigModel.notification.scoreAlertThreshold"
                  :min="0"
                  :max="100"
                  controls-position="right"
                  class="w-100%"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElFormItem label="评审通知平台">
            <ElCheckboxGroup v-model="form.reviewConfigModel.notification.scoreAlertChannels">
              <ElCheckbox
                v-for="item in notificationPlatformOptions"
                :key="item.value"
                :value="item.value"
                :disabled="!form.reviewConfigModel.notification.platforms[item.value].enabled"
              >
                {{ item.label }}
              </ElCheckbox>
            </ElCheckboxGroup>
          </ElFormItem>
          <ElRow :gutter="16">
            <ElCol :span="8">
              <ElFormItem label="最大重试次数">
                <ElInputNumber
                  v-model="form.reviewConfigModel.notification.retry.maxRetries"
                  :min="0"
                  :max="10"
                  controls-position="right"
                  class="w-100%"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="总超时秒数">
                <ElInputNumber
                  v-model="form.reviewConfigModel.notification.retry.timeoutSec"
                  :min="1"
                  :max="3600"
                  controls-position="right"
                  class="w-100%"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="退避秒数">
                <ElSelect
                  v-model="form.reviewConfigModel.notification.retry.backoffSeconds"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  class="w-100%"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
        </ElTabPane>

        <ElTabPane label="代码索引" name="parser">
          <ElRow :gutter="16">
            <ElCol :span="8">
              <ElFormItem label="最大关联类数">
                <ElInputNumber
                  v-model="form.maxRelatedClasses"
                  :min="1"
                  controls-position="right"
                  class="w-100%"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="最大上下文 Token">
                <ElInputNumber
                  v-model="form.maxContextTokens"
                  :min="1000"
                  :step="1000"
                  controls-position="right"
                  class="w-100%"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="评审并发度">
                <ElInputNumber
                  v-model="form.reviewParallelism"
                  :min="1"
                  controls-position="right"
                  class="w-100%"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
        </ElTabPane>
      </ElTabs>
    </ElForm>

    <template #footer>
      <BaseButton type="primary" :loading="saveLoading" @click="save">保存</BaseButton>
      <BaseButton @click="formVisible = false">取消</BaseButton>
    </template>
  </Dialog>

  <Dialog
    v-model="detailVisible"
    title="SCM 配置详情"
    width="760px"
    max-height="calc(100vh - 170px)"
  >
    <div v-if="currentRow" class="scm-detail">
      <div
        ><span>仓库</span
        ><strong>{{ currentRow.projectName || composeRepo(currentRow) }}</strong></div
      >
      <div
        ><span>平台</span><strong>{{ currentRow.scmProvider || '-' }}</strong></div
      >
      <div
        ><span>项目 ID</span><strong>{{ currentRow.projectId || '-' }}</strong></div
      >
      <div
        ><span>仓库路径</span><strong>{{ composeRepo(currentRow) }}</strong></div
      >
      <div
        ><span>API 地址</span
        ><strong>{{ currentRow.apiBaseUrl || providerDefault('apiBaseUrl') || '-' }}</strong></div
      >
      <div
        ><span>Web 地址</span
        ><strong>{{ currentRow.webBaseUrl || providerDefault('webBaseUrl') || '-' }}</strong></div
      >
      <div
        ><span>触发规则</span><strong>{{ summarizeTrigger(currentRow) }}</strong></div
      >
      <div
        ><span>通知</span><strong>{{ notifyText(currentRow) }}</strong></div
      >
      <div
        ><span>绑定应用数</span><strong>{{ linkageSummary(currentRow).count }} 个</strong></div
      >
      <div
        ><span>联动摘要</span><strong>{{ linkageSummary(currentRow).description }}</strong></div
      >
      <div
        ><span>源码索引</span><strong>{{ codeIndexDisplayStatus(currentRow).label }}</strong></div
      >
      <div
        ><span>索引摘要</span><strong>{{ codeIndexSummaryText(currentRow) }}</strong></div
      >
      <div
        ><span>索引更新时间</span><strong>{{ codeIndexTimeText(currentRow) }}</strong></div
      >
      <div
        ><span>启用平台</span
        ><strong>{{
          getEnabledPlatforms(currentRow)
            .map((item) => item.label)
            .join(' / ') || '-'
        }}</strong></div
      >
      <div
        ><span>更新时间</span
        ><strong>{{ formatDate(currentRow.updateTime || currentRow.createTime) }}</strong></div
      >
    </div>
    <template #footer>
      <BaseButton :icon="Link" @click="goAppLinkage(currentRow)">前往应用联动配置</BaseButton>
      <BaseButton @click="detailVisible = false">关闭</BaseButton>
    </template>
  </Dialog>
</template>

<style scoped>
.scm-detail {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.scm-detail div {
  min-width: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.scm-detail span {
  display: block;
  margin-bottom: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.scm-detail strong {
  display: block;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.platform-config-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.platform-config-item {
  padding: 12px 12px 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
</style>
