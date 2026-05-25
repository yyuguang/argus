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
  buildEmptyAppLinkageForm,
  buildEmptyErrorAlertRoutes,
  deleteAppLinkageApi,
  getAppLinkagePageApi,
  getAppLinkageScmOptionsApi,
  routeEnabledCount,
  routeSummaryText,
  saveAppLinkageApi,
  toAppLinkageForm
} from '@/api/appLinkage'
import {
  bindAppVersionApi,
  getCodeIndexPageApi,
  getCurrentAppVersionBindingApi
} from '@/api/codeIndex'
import type {
  AppLinkageCodeIndexStatus,
  AppLinkageFormModel,
  AppLinkageHealthStatus,
  AppLinkageListItem,
  AppLinkagePageParams,
  AppLinkageScmOption,
  AppLinkageVersionBindingFormModel
} from '@/api/appLinkage/types'
import type { AppVersionBindingItem, CodeIndexSummaryItem } from '@/api/codeIndex/types'
import { computed, onMounted, reactive, ref, unref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElForm,
  ElFormItem,
  ElInput,
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
import { Delete, Edit, Link, Plus, Refresh, Setting, View } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const providerOptions = [
  { label: 'GitLab', value: 'gitlab' },
  { label: 'GitHub', value: 'github' },
  { label: 'Gitee', value: 'gitee' }
]

const healthOptions = [
  { label: '可定位', value: 'READY' },
  { label: '待补齐', value: 'INCOMPLETE' },
  { label: '仓库缺失', value: 'SCM_MISSING' }
]
const bindingEnvironmentOptions = [
  { label: 'prod', value: 'prod' },
  { label: 'test', value: 'test' },
  { label: 'staging', value: 'staging' },
  { label: 'dev', value: 'dev' }
]

const severityOptions = ['P0', 'P1', 'P2', 'P3']
const routeChannelOptions = [
  { label: 'critical', value: 'critical' },
  { label: 'default', value: 'default' }
]
const routePriorityOptions = [
  { label: 'urgent', value: 'urgent' },
  { label: 'normal', value: 'normal' },
  { label: 'low', value: 'low' }
]

const providerLabelMap = new Map(providerOptions.map((item) => [item.value, item.label]))
type TagType = 'success' | 'warning' | 'info' | 'primary' | 'danger'

interface LinkageRuntimeState {
  latestIndex?: CodeIndexSummaryItem
  currentBinding?: AppVersionBindingItem
}

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
    field: 'healthStatus',
    label: '联动状态',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: healthOptions
    }
  },
  {
    field: 'scmConfigId',
    label: '仓库',
    component: 'Select',
    componentProps: {
      clearable: true,
      filterable: true,
      options: []
    }
  },
  {
    field: 'keyword',
    label: '关键字',
    component: 'Input',
    componentProps: {
      placeholder: 'appName / 仓库 / 源码路径',
      maxlength: 100
    }
  }
])

const composeRepo = (
  row?: { projectName?: string; repoOwner?: string; repoName?: string } | null
) => {
  if (!row) return '-'
  if (row.projectName) return row.projectName
  if (row.repoOwner && row.repoName) return `${row.repoOwner}/${row.repoName}`
  return row.repoName || row.repoOwner || '-'
}

const formatDate = (value?: string) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const providerTagType = (provider?: string) => {
  if (provider === 'gitlab') return 'warning'
  if (provider === 'github') return 'info'
  if (provider === 'gitee') return 'danger'
  return 'primary'
}

const healthTagType = (status?: AppLinkageHealthStatus) => {
  if (status === 'READY') return 'success'
  if (status === 'INCOMPLETE') return 'warning'
  return 'danger'
}

const routeTagType = (severity: string) => {
  if (severity === 'P0') return 'danger'
  if (severity === 'P1') return 'warning'
  if (severity === 'P2') return 'primary'
  return 'info'
}

const rowRuntimeKey = (row?: AppLinkageListItem) => {
  if (!row) {
    return ''
  }
  if (row.id) {
    return String(row.id)
  }
  return `${row.scmConfigId || ''}:${row.appName || ''}`
}

const resolveBindingEnvironment = (row?: AppLinkageListItem) => {
  return row?.environment || bindingEnvironment.value || 'prod'
}

const setRuntimeLoading = (row: AppLinkageListItem, value: boolean) => {
  const key = rowRuntimeKey(row)
  if (!key) {
    return
  }
  runtimeLoadingMap.value = {
    ...runtimeLoadingMap.value,
    [key]: value
  }
}

const putRuntimeState = (row: AppLinkageListItem, state?: LinkageRuntimeState) => {
  const key = rowRuntimeKey(row)
  if (!key) {
    return
  }
  runtimeByMapping.value = {
    ...runtimeByMapping.value,
    [key]: state
  }
}

const runtimeState = (row?: AppLinkageListItem) => {
  const key = rowRuntimeKey(row)
  return key ? runtimeByMapping.value[key] : undefined
}

const isRuntimeLoading = (row?: AppLinkageListItem) => {
  const key = rowRuntimeKey(row)
  return key ? runtimeLoadingMap.value[key] === true : false
}

const loadRuntimeState = async (row: AppLinkageListItem) => {
  if (!row.scmConfigId || !row.appName) {
    putRuntimeState(row, undefined)
    return
  }
  setRuntimeLoading(row, true)
  try {
    const [indexResult, bindingResult] = await Promise.allSettled([
      getCodeIndexPageApi({
        pageIndex: 1,
        pageSize: 1,
        scmConfigId: row.scmConfigId,
        branchName: row.defaultBranch || undefined
      }),
      getCurrentAppVersionBindingApi({
        appName: row.appName,
        environment: resolveBindingEnvironment(row),
        scmConfigId: row.scmConfigId
      })
    ])
    putRuntimeState(row, {
      latestIndex:
        indexResult.status === 'fulfilled' ? indexResult.value.data?.list?.[0] : undefined,
      currentBinding:
        bindingResult.status === 'fulfilled' ? bindingResult.value.data || undefined : undefined
    })
  } finally {
    setRuntimeLoading(row, false)
  }
}

const loadRuntimeStates = async (rows: AppLinkageListItem[]) => {
  await Promise.allSettled(rows.map((row) => loadRuntimeState(row)))
}

const normalizeIndexStatus = (
  status?: string,
  warningCount?: number
): AppLinkageCodeIndexStatus => {
  if (!status) {
    return 'NOT_INDEXED'
  }
  if (status === 'SUCCESS' && Number(warningCount || 0) > 0) {
    return 'WARNING'
  }
  if (status === 'PENDING') {
    return 'RUNNING'
  }
  if (status === 'PARTIAL') {
    return 'WARNING'
  }
  if (['SUCCESS', 'RUNNING', 'FAILED', 'WARNING', 'BOUND', 'BUILDING'].includes(status)) {
    return status as AppLinkageCodeIndexStatus
  }
  return 'WARNING'
}

const codeIndexStatusDisplay = (
  row?: AppLinkageListItem
): {
  label: AppLinkageCodeIndexStatus
  type: TagType
} => {
  if (isRuntimeLoading(row)) {
    return {
      label: 'RUNNING',
      type: 'warning'
    }
  }
  const state = runtimeState(row)
  const latestIndex = state?.latestIndex
  const status = normalizeIndexStatus(
    state?.currentBinding?.indexStatus || latestIndex?.scanStatus,
    latestIndex?.warningCount
  )
  if (status === 'SUCCESS') return { label: status, type: 'success' }
  if (status === 'FAILED') return { label: status, type: 'danger' }
  if (status === 'BOUND') return { label: status, type: 'primary' }
  if (status === 'NOT_INDEXED') return { label: status, type: 'info' }
  return { label: status, type: 'warning' }
}

const shortCommitSha = (value?: string) => {
  return value ? value.slice(0, 8) : '-'
}

const activeVersionText = (row?: AppLinkageListItem) => {
  const binding = runtimeState(row)?.currentBinding
  if (!binding) {
    return '未绑定'
  }
  return binding.versionName || shortCommitSha(binding.commitSha)
}

const indexSummaryText = (row?: AppLinkageListItem) => {
  if (isRuntimeLoading(row)) {
    return '读取中'
  }
  const index = runtimeState(row)?.latestIndex
  if (!index) {
    return '暂无索引'
  }
  return `${index.branchName || row?.defaultBranch || '-'}@${shortCommitSha(index.commitSha)} · ${
    index.classCount || 0
  } 类`
}

const indexWarningText = (row?: AppLinkageListItem) => {
  const state = runtimeState(row)
  const index = state?.latestIndex
  if (index?.latestErrorMessage) {
    return index.latestErrorMessage
  }
  if (Number(index?.warningCount || 0) > 0) {
    return `存在 ${index?.warningCount} 个扫描告警`
  }
  if (state?.currentBinding?.indexStatus === 'BUILDING') {
    return '当前绑定 commit 尚未关联成功索引'
  }
  return '-'
}

const bindingBranchText = (row?: AppLinkageListItem) => {
  return runtimeState(row)?.currentBinding?.branchName || row?.defaultBranch || '-'
}

const resetBindingForm = () => {
  Object.assign(bindingForm, {
    mappingId: undefined,
    appName: '',
    environment: bindingEnvironment.value || 'prod',
    scmConfigId: undefined,
    branchName: '',
    commitSha: '',
    versionName: '',
    remark: ''
  })
}

const searchParams = ref<AppLinkagePageParams>({})
const scmOptions = ref<AppLinkageScmOption[]>([])
const optionsLoading = ref(false)
const formVisible = ref(false)
const detailVisible = ref(false)
const bindingVisible = ref(false)
const saveLoading = ref(false)
const bindingLoading = ref(false)
const activeFormTab = ref('basic')
const activeDetailTab = ref('basic')
const formRef = ref<FormInstance>()
const currentRow = ref<AppLinkageListItem>()
const bindingTargetRow = ref<AppLinkageListItem>()
const bindingEnvironment = ref('prod')
const runtimeByMapping = ref<Record<string, LinkageRuntimeState | undefined>>({})
const runtimeLoadingMap = ref<Record<string, boolean>>({})
const form = reactive<AppLinkageFormModel>(buildEmptyAppLinkageForm())
const bindingForm = reactive<AppLinkageVersionBindingFormModel>({
  appName: '',
  environment: 'prod',
  branchName: '',
  commitSha: '',
  versionName: '',
  remark: ''
})

const dialogTitle = computed(() => (form.id ? '编辑应用联动' : '新增应用联动'))
const bindingDialogTitle = computed(() =>
  bindingTargetRow.value?.appName
    ? `更新 ${bindingTargetRow.value.appName} 版本绑定`
    : '更新版本绑定'
)
const selectedScmOption = computed(() =>
  scmOptions.value.find((item) => String(item.id) === String(form.scmConfigId || ''))
)

const updateScmSearchOptions = () => {
  const options = scmOptions.value.map((item) => ({
    label: `${item.projectName} · ${providerLabelMap.get(item.scmProvider) || item.scmProvider} · ${item.projectId}`,
    value: item.id
  }))
  const schema = searchSchema.find((item) => item.field === 'scmConfigId')
  if (schema) {
    schema.componentProps = {
      ...(schema.componentProps || {}),
      options
    }
  }
}

const loadScmOptions = async () => {
  optionsLoading.value = true
  try {
    const res = await getAppLinkageScmOptionsApi()
    scmOptions.value = res.data || []
    updateScmSearchOptions()
  } finally {
    optionsLoading.value = false
  }
}

const { tableRegister, tableState, tableMethods } = useTable({
  immediate: false,
  fetchDataApi: async () => {
    const { pageSize, currentPage } = tableState
    const res = await getAppLinkagePageApi({
      pageIndex: unref(currentPage),
      pageSize: unref(pageSize),
      ...unref(searchParams)
    })
    const list = res.data.list || []
    await loadRuntimeStates(list)
    return {
      list,
      total: res.data.total || 0
    }
  }
})
const { dataList, loading, total, currentPage, pageSize } = tableState
const { getList } = tableMethods

const rules = reactive<FormRules<AppLinkageFormModel>>({
  scmConfigId: [{ required: true, message: '请选择 SCM 仓库', trigger: 'change' }],
  appName: [{ required: true, message: 'appName 不能为空', trigger: 'blur' }],
  sourceRoot: [{ required: true, message: '服务源码根不能为空', trigger: 'blur' }],
  defaultBranch: [{ required: true, message: '默认分支不能为空', trigger: 'blur' }]
})

const tableColumns = reactive<TableColumn[]>([
  {
    field: 'index',
    label: '序号',
    type: 'index'
  },
  {
    field: 'appName',
    label: '应用',
    minWidth: 180,
    slots: {
      default: ({ row }: { row: AppLinkageListItem }) => (
        <div>
          <div class="font-600">{row.appName || '-'}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            环境: {resolveBindingEnvironment(row)}
          </div>
        </div>
      )
    }
  },
  {
    field: 'projectName',
    label: 'SCM 仓库',
    minWidth: 280,
    slots: {
      default: ({ row }: { row: AppLinkageListItem }) => (
        <div>
          <div class="font-600">{composeRepo(row)}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            项目 ID {row.scmProjectId || '-'}
          </div>
        </div>
      )
    }
  },
  {
    field: 'scmProvider',
    label: '平台',
    width: 110,
    slots: {
      default: ({ row }: { row: AppLinkageListItem }) => (
        <ElTag type={providerTagType(row.scmProvider)} effect="light">
          {providerLabelMap.get(row.scmProvider || '') || row.scmProvider || '-'}
        </ElTag>
      )
    }
  },
  {
    field: 'versionIndex',
    label: '版本索引',
    minWidth: 300,
    slots: {
      default: ({ row }: { row: AppLinkageListItem }) => {
        const display = codeIndexStatusDisplay(row)
        return (
          <div class="text-12px leading-20px">
            <div>默认分支: {row.defaultBranch || '-'}</div>
            <div>当前版本: {activeVersionText(row)}</div>
            <div>
              <ElTag type={display.type} effect="light">
                {display.label}
              </ElTag>
              <span class="ml-6px color-[var(--el-text-color-secondary)]">
                {indexSummaryText(row)}
              </span>
            </div>
          </div>
        )
      }
    }
  },
  {
    field: 'errorAlertRoutes',
    label: '错误告警路由',
    minWidth: 260,
    slots: {
      default: ({ row }: { row: AppLinkageListItem }) => (
        <div>
          <div class="font-600">{routeEnabledCount(row.errorAlertRoutes)}/4 已启用</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            {routeSummaryText(row.errorAlertRoutes)}
          </div>
        </div>
      )
    }
  },
  {
    field: 'healthStatus',
    label: '联动状态',
    width: 130,
    slots: {
      default: ({ row }: { row: AppLinkageListItem }) => (
        <ElTag type={healthTagType(row.healthStatus)}>{row.healthLabel}</ElTag>
      )
    }
  },
  {
    field: 'updateTime',
    label: '更新时间',
    width: 180,
    slots: {
      default: ({ row }: { row: AppLinkageListItem }) => (
        <>{formatDate(row.updateTime || row.createTime)}</>
      )
    }
  },
  {
    field: 'action',
    label: '操作',
    width: 380,
    fixed: 'right',
    slots: {
      default: ({ row }: { row: AppLinkageListItem }) => (
        <div class="flex flex-wrap gap-8px">
          {hasPermi('view') ? (
            <BaseButton type="success" icon={View} onClick={() => openDetail(row)}>
              详情
            </BaseButton>
          ) : null}
          {hasPermi('update') ? (
            <BaseButton type="primary" icon={Edit} onClick={() => openEdit(row)}>
              编辑
            </BaseButton>
          ) : null}
          {hasPermi('update') ? (
            <BaseButton icon={Link} onClick={() => openBindingDialog(row)}>
              绑定版本
            </BaseButton>
          ) : null}
          {hasPermi('update') ? (
            <BaseButton icon={Setting} onClick={() => goDataMonitor(row)}>
              数据监控
            </BaseButton>
          ) : null}
          {hasPermi('delete') ? (
            <BaseButton type="danger" icon={Delete} onClick={() => removeLinkage(row)}>
              删除
            </BaseButton>
          ) : null}
        </div>
      )
    }
  }
])

const setSearchParams = (params: Record<string, unknown>) => {
  searchParams.value = {
    scmProvider: String(params.scmProvider || '') || undefined,
    scmConfigId: params.scmConfigId ? String(params.scmConfigId) : undefined,
    healthStatus: (params.healthStatus as AppLinkageHealthStatus) || undefined,
    keyword: String(params.keyword || '') || undefined
  }
  currentPage.value = 1
  getList()
}

const applyRouteQuery = () => {
  const nextParams: Record<string, unknown> = {}

  if (route.query.scmConfigId) {
    nextParams.scmConfigId = String(route.query.scmConfigId)
  }
  if (route.query.appName) {
    nextParams.keyword = String(route.query.appName)
  }
  if (!Object.keys(nextParams).length) {
    return false
  }
  setSearchParams(nextParams)
  return true
}

const resetForm = () => {
  Object.assign(form, buildEmptyAppLinkageForm())
  formRef.value?.clearValidate()
}

const applyScmDefaults = (forceRoute = false) => {
  const option = selectedScmOption.value
  if (!option) return
  if (!form.sourceRoot || form.sourceRoot === 'src/main/java') {
    form.sourceRoot = option.defaultSourceRoot || form.sourceRoot
  }
  if (!form.basePackage) {
    form.basePackage = option.defaultBasePackage || ''
  }
  if (forceRoute) {
    form.errorAlertRoutes = JSON.parse(
      JSON.stringify(option.errorAlertRoutes || buildEmptyErrorAlertRoutes())
    )
  }
}

watch(
  () => form.scmConfigId,
  (value, oldValue) => {
    if (!value) {
      form.errorAlertRoutes = buildEmptyErrorAlertRoutes()
      return
    }
    if (value !== oldValue) {
      applyScmDefaults(true)
    }
  }
)

const openCreate = () => {
  if (!scmOptions.value.length) {
    ElMessage.warning('请先在 SCM 配置中维护带项目 ID 的仓库，再新增应用联动')
    return
  }
  resetForm()
  form.scmConfigId = scmOptions.value[0]?.id
  applyScmDefaults(true)
  activeFormTab.value = 'basic'
  formVisible.value = true
}

const openEdit = (row: AppLinkageListItem) => {
  resetForm()
  Object.assign(form, toAppLinkageForm(row))
  activeFormTab.value = 'basic'
  formVisible.value = true
}

const openDetail = (row: AppLinkageListItem) => {
  currentRow.value = row
  activeDetailTab.value = 'basic'
  detailVisible.value = true
}

const openBindingDialog = (row: AppLinkageListItem) => {
  if (!row.scmConfigId) {
    ElMessage.warning('当前联动未绑定有效的 SCM 仓库')
    return
  }
  resetBindingForm()
  bindingTargetRow.value = row
  const state = runtimeState(row)
  const binding = state?.currentBinding
  const latestIndex = state?.latestIndex
  Object.assign(bindingForm, {
    mappingId: row.id,
    appName: row.appName || '',
    environment: binding?.environment || resolveBindingEnvironment(row),
    scmConfigId: row.scmConfigId,
    branchName: binding?.branchName || latestIndex?.branchName || row.defaultBranch || '',
    commitSha: binding?.commitSha || latestIndex?.commitSha || '',
    versionName: binding?.versionName || '',
    remark: ''
  })
  bindingVisible.value = true
}

const openCurrentBindingDialog = () => {
  if (currentRow.value) {
    openBindingDialog(currentRow.value)
  }
}

const saveVersionBinding = async () => {
  const targetRow = bindingTargetRow.value
  if (!targetRow) {
    return
  }
  if (!bindingForm.appName || !bindingForm.environment || !bindingForm.scmConfigId) {
    ElMessage.warning('应用、环境和 SCM 仓库不能为空')
    return
  }
  if (!bindingForm.commitSha.trim()) {
    ElMessage.warning('commitSha 不能为空')
    return
  }
  bindingLoading.value = true
  try {
    const res = await bindAppVersionApi({
      mappingId: bindingForm.mappingId,
      appName: bindingForm.appName,
      environment: bindingForm.environment.trim(),
      scmConfigId: bindingForm.scmConfigId,
      branchName: bindingForm.branchName.trim() || targetRow.defaultBranch,
      commitSha: bindingForm.commitSha.trim(),
      versionName: bindingForm.versionName.trim() || undefined,
      bindingSource: 'MANUAL',
      remark: bindingForm.remark.trim() || undefined
    })
    bindingEnvironment.value = bindingForm.environment.trim()
    putRuntimeState(targetRow, {
      ...(runtimeState(targetRow) || {}),
      currentBinding: res.data
    })
    await loadRuntimeState(targetRow)
    ElMessage.success('应用版本绑定已更新')
    bindingVisible.value = false
  } finally {
    bindingLoading.value = false
  }
}

const save = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!/^[A-Za-z0-9_.-]{2,100}$/.test(String(form.appName || '').trim())) {
    ElMessage.warning('appName 仅支持字母、数字、下划线、中划线和点号，长度 2-100')
    return
  }
  if (form.sourceRoot.startsWith('/')) {
    ElMessage.warning('服务源码根不能以 / 开头')
    return
  }

  saveLoading.value = true
  try {
    await saveAppLinkageApi(form)
    ElMessage.success(form.id ? '应用联动已更新' : '应用联动已创建')
    formVisible.value = false
    await loadScmOptions()
    getList()
  } finally {
    saveLoading.value = false
  }
}

const removeLinkage = async (row: AppLinkageListItem) => {
  if (!row.id) return
  await ElMessageBox.confirm(
    `删除后，${row.appName} 的错误日志将无法完成源码定位和数据监控关联。确认删除吗？`,
    '删除应用联动',
    { type: 'warning' }
  )
  await deleteAppLinkageApi(row.id)
  ElMessage.success('应用联动已删除')
  getList()
}

const goDataMonitor = (row?: AppLinkageListItem) => {
  if (!row?.id) {
    ElMessage.warning('请先保存应用联动后再进入监控配置页')
    return
  }
  router.push({
    path: '/monitor-center/data-monitor-config',
    query: {
      mappingId: row.id
    }
  })
}

const goScmConfig = (row?: AppLinkageListItem) => {
  if (!row?.scmConfigId) {
    ElMessage.warning('当前联动未绑定有效的 SCM 仓库')
    return
  }
  router.push({
    path: '/code-review/scm-config'
  })
}

const refreshAll = async () => {
  await loadScmOptions()
  await getList()
}

onMounted(async () => {
  await loadScmOptions()
  if (!applyRouteQuery()) {
    await getList()
  }
})
</script>

<template>
  <ContentWrap>
    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="mb-12px"
      title="应用联动负责 appName 与仓库源码位置的绑定；错误告警路由仍然是仓库级配置，在这里编辑会影响该仓库下所有联动应用。"
    />
    <Search :schema="searchSchema" @reset="setSearchParams" @search="setSearchParams" />
    <div class="mb-10px flex gap-10px">
      <BaseButton v-if="hasPermi('create')" type="primary" :icon="Plus" @click="openCreate">
        新增联动
      </BaseButton>
      <ElSelect
        v-model="bindingEnvironment"
        class="!w-130px"
        filterable
        allow-create
        default-first-option
        @change="getList"
      >
        <ElOption
          v-for="item in bindingEnvironmentOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </ElSelect>
      <BaseButton :icon="Refresh" :loading="loading || optionsLoading" @click="refreshAll">
        刷新
      </BaseButton>
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

  <Dialog v-model="formVisible" :title="dialogTitle" width="980px" max-height="calc(100vh - 170px)">
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top">
      <ElTabs v-model="activeFormTab">
        <ElTabPane label="基础联动" name="basic">
          <ElRow :gutter="16">
            <ElCol :span="12">
              <ElFormItem label="SCM 仓库" prop="scmConfigId">
                <ElSelect
                  v-model="form.scmConfigId"
                  class="w-100%"
                  filterable
                  placeholder="选择 SCM 仓库"
                >
                  <ElOption
                    v-for="item in scmOptions"
                    :key="item.id"
                    :label="`${item.projectName} · ${providerLabelMap.get(item.scmProvider) || item.scmProvider}`"
                    :value="item.id"
                  />
                </ElSelect>
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="平台 / 项目 ID">
                <ElInput
                  :model-value="
                    selectedScmOption
                      ? `${providerLabelMap.get(selectedScmOption.scmProvider) || selectedScmOption.scmProvider} / ${selectedScmOption.projectId}`
                      : '-'
                  "
                  readonly
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElRow :gutter="16">
            <ElCol :span="8">
              <ElFormItem label="appName" prop="appName">
                <ElInput v-model.trim="form.appName" maxlength="100" placeholder="例如 argus-api" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="服务源码根" prop="sourceRoot">
                <ElInput
                  v-model.trim="form.sourceRoot"
                  maxlength="255"
                  placeholder="例如 src/main/java"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="默认分支" prop="defaultBranch">
                <ElInput
                  v-model.trim="form.defaultBranch"
                  maxlength="100"
                  placeholder="例如 master"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElRow :gutter="16">
            <ElCol :span="24">
              <ElFormItem label="基础包">
                <ElInput
                  v-model.trim="form.basePackage"
                  maxlength="255"
                  placeholder="例如 com.lnzz.argus"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElAlert
            type="warning"
            :closable="false"
            show-icon
            title="基础包当前允许为空，但为空时源码定位与类名回溯能力会变弱。"
          />
        </ElTabPane>

        <ElTabPane label="错误告警路由" name="routes">
          <ElAlert
            type="info"
            :closable="false"
            show-icon
            class="mb-12px"
            title="这里修改的是仓库级错误告警路由，不是单个 appName 独享配置。"
          />
          <ElRow :gutter="16" class="route-header">
            <ElCol :span="4"><strong>级别</strong></ElCol>
            <ElCol :span="4"><strong>是否启用</strong></ElCol>
            <ElCol :span="8"><strong>通知通道</strong></ElCol>
            <ElCol :span="8"><strong>优先级</strong></ElCol>
          </ElRow>
          <ElRow v-for="severity in severityOptions" :key="severity" :gutter="16" class="mb-12px">
            <ElCol :span="4">
              <ElTag :type="routeTagType(severity)">{{ severity }}</ElTag>
            </ElCol>
            <ElCol :span="4">
              <ElSwitch v-model="form.errorAlertRoutes[severity].enabled" />
            </ElCol>
            <ElCol :span="8">
              <ElSelect v-model="form.errorAlertRoutes[severity].channel" class="w-100%">
                <ElOption
                  v-for="item in routeChannelOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElCol>
            <ElCol :span="8">
              <ElSelect v-model="form.errorAlertRoutes[severity].priority" class="w-100%">
                <ElOption
                  v-for="item in routePriorityOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElCol>
          </ElRow>
        </ElTabPane>

        <ElTabPane label="扩展入口" name="extensions">
          <ElDescriptions :column="1" border>
            <ElDescriptionsItem label="已选仓库">
              {{ selectedScmOption ? selectedScmOption.projectName : '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="数据监控">
              应用联动保存后，可继续进入“数据监控配置”维护只读数据源、慢 SQL、连接池与日志表监控。
            </ElDescriptionsItem>
          </ElDescriptions>
        </ElTabPane>
      </ElTabs>
    </ElForm>
    <template #footer>
      <BaseButton @click="formVisible = false">取消</BaseButton>
      <BaseButton type="primary" :loading="saveLoading" @click="save">保存</BaseButton>
    </template>
  </Dialog>

  <Dialog
    v-model="detailVisible"
    title="应用联动详情"
    width="920px"
    max-height="calc(100vh - 170px)"
  >
    <template v-if="currentRow">
      <ElTabs v-model="activeDetailTab">
        <ElTabPane label="基础联动" name="basic">
          <ElDescriptions :column="2" border>
            <ElDescriptionsItem label="appName">{{ currentRow.appName || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="SCM 仓库">{{ composeRepo(currentRow) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="平台">
              <ElTag :type="providerTagType(currentRow.scmProvider)" effect="light">
                {{
                  providerLabelMap.get(currentRow.scmProvider || '') ||
                  currentRow.scmProvider ||
                  '-'
                }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="联动状态">
              <ElTag :type="healthTagType(currentRow.healthStatus)">{{
                currentRow.healthLabel
              }}</ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="项目 ID">{{
              currentRow.scmProjectId || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="默认分支">{{
              currentRow.defaultBranch || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="服务源码根">{{
              currentRow.sourceRoot || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="基础包">{{
              currentRow.basePackage || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="更新时间">
              {{ formatDate(currentRow.updateTime || currentRow.createTime) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="说明">{{ currentRow.healthDescription }}</ElDescriptionsItem>
          </ElDescriptions>
        </ElTabPane>

        <ElTabPane label="版本索引" name="version">
          <ElAlert
            v-if="indexWarningText(currentRow) !== '-'"
            type="warning"
            :closable="false"
            show-icon
            class="mb-12px"
            :title="indexWarningText(currentRow)"
          />
          <ElDescriptions :column="2" border>
            <ElDescriptionsItem label="当前环境">
              {{ resolveBindingEnvironment(currentRow) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="索引状态">
              <ElTag :type="codeIndexStatusDisplay(currentRow).type">
                {{ codeIndexStatusDisplay(currentRow).label }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="当前版本">
              {{ activeVersionText(currentRow) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="绑定来源">
              {{ runtimeState(currentRow)?.currentBinding?.bindingSource || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="绑定分支">
              {{ bindingBranchText(currentRow) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="绑定 Commit">
              {{ runtimeState(currentRow)?.currentBinding?.commitSha || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="最新索引">
              {{ indexSummaryText(currentRow) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="索引更新时间">
              {{
                formatDate(
                  runtimeState(currentRow)?.latestIndex?.finishedAt ||
                    runtimeState(currentRow)?.latestIndex?.updateTime ||
                    runtimeState(currentRow)?.latestIndex?.createTime
                )
              }}
            </ElDescriptionsItem>
          </ElDescriptions>
          <div class="mt-12px">
            <BaseButton type="primary" :icon="Link" @click="openCurrentBindingDialog">
              更新版本绑定
            </BaseButton>
          </div>
        </ElTabPane>

        <ElTabPane label="错误告警路由" name="routes">
          <ElAlert
            type="info"
            :closable="false"
            show-icon
            class="mb-12px"
            title="该路由摘要来自所属 SCM 仓库，当前详情仅做展示。"
          />
          <ElRow :gutter="16" class="route-header">
            <ElCol :span="4"><strong>级别</strong></ElCol>
            <ElCol :span="6"><strong>启用</strong></ElCol>
            <ElCol :span="7"><strong>通道</strong></ElCol>
            <ElCol :span="7"><strong>优先级</strong></ElCol>
          </ElRow>
          <ElRow v-for="severity in severityOptions" :key="severity" :gutter="16" class="mb-12px">
            <ElCol :span="4">
              <ElTag :type="routeTagType(severity)">{{ severity }}</ElTag>
            </ElCol>
            <ElCol :span="6">
              <ElTag :type="currentRow.errorAlertRoutes[severity]?.enabled ? 'success' : 'info'">
                {{ currentRow.errorAlertRoutes[severity]?.enabled ? '启用' : '关闭' }}
              </ElTag>
            </ElCol>
            <ElCol :span="7">{{ currentRow.errorAlertRoutes[severity]?.channel || '-' }}</ElCol>
            <ElCol :span="7">{{ currentRow.errorAlertRoutes[severity]?.priority || '-' }}</ElCol>
          </ElRow>
        </ElTabPane>

        <ElTabPane label="扩展入口" name="extensions">
          <div class="flex flex-wrap gap-10px">
            <BaseButton type="primary" :icon="Setting" @click="goDataMonitor(currentRow)">
              前往数据监控配置
            </BaseButton>
            <BaseButton :icon="Link" @click="goScmConfig(currentRow)">前往 SCM 配置</BaseButton>
          </div>
        </ElTabPane>
      </ElTabs>
    </template>
  </Dialog>

  <Dialog
    v-model="bindingVisible"
    :title="bindingDialogTitle"
    width="720px"
    max-height="calc(100vh - 170px)"
  >
    <ElForm :model="bindingForm" label-position="top">
      <ElRow :gutter="16">
        <ElCol :span="12">
          <ElFormItem label="appName">
            <ElInput v-model="bindingForm.appName" readonly />
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="环境">
            <ElSelect
              v-model="bindingForm.environment"
              class="w-100%"
              filterable
              allow-create
              default-first-option
            >
              <ElOption
                v-for="item in bindingEnvironmentOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
      </ElRow>
      <ElRow :gutter="16">
        <ElCol :span="12">
          <ElFormItem label="分支">
            <ElInput v-model.trim="bindingForm.branchName" maxlength="100" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="版本名称">
            <ElInput
              v-model.trim="bindingForm.versionName"
              maxlength="120"
              placeholder="发布单、镜像 tag 或构建号"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>
      <ElFormItem label="Commit SHA">
        <ElInput
          v-model.trim="bindingForm.commitSha"
          maxlength="80"
          placeholder="请输入当前部署版本对应的 commit"
        />
      </ElFormItem>
      <ElFormItem label="备注">
        <ElInput v-model.trim="bindingForm.remark" type="textarea" :rows="3" maxlength="300" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <BaseButton @click="bindingVisible = false">取消</BaseButton>
      <BaseButton type="primary" :loading="bindingLoading" @click="saveVersionBinding">
        保存绑定
      </BaseButton>
    </template>
  </Dialog>
</template>

<style scoped>
.route-header {
  margin-bottom: 12px;
}
</style>
