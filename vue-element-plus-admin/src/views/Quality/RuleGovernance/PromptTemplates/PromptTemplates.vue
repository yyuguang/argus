<script setup lang="tsx">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ContentWrap } from '@/components/ContentWrap'
import { Search } from '@/components/Search'
import { Table, TableColumn } from '@/components/Table'
import { BaseButton } from '@/components/Button'
import { FormSchema } from '@/components/Form'
import { hasPermi } from '@/components/Permission/src/utils'
import { useTable } from '@/hooks/web/useTable'
import { listScmConfigsApi } from '@/api/scm'
import type { ScmConfigItem } from '@/api/scm/types'
import {
  deletePromptScmOverrideApi,
  getPromptCatalogApi,
  getPromptEffectiveTemplateApi,
  getPromptGlobalSchemesApi,
  getPromptScmSchemesApi,
  getPromptTemplateDetailApi,
  savePromptGlobalSchemeApi,
  savePromptScmSchemeApi
} from '@/api/rule'
import type {
  PromptCatalogCategoryItem,
  PromptTemplateQueryParams,
  PromptTemplateSavePayload,
  PromptTemplateSchemeItem
} from '@/api/rule/types'
import { ElAlert, ElMessage, ElMessageBox, ElTag } from 'element-plus'
import { Edit, Plus, Refresh, RefreshLeft, View } from '@element-plus/icons-vue'
import RuleScmContextBar from '../components/RuleScmContextBar.vue'
import PromptCategorySummary from './components/PromptCategorySummary.vue'
import PromptTemplateEditorDialog from './components/PromptTemplateEditorDialog.vue'

type EffectiveScopeFilter = '' | 'GLOBAL' | 'SCM'

interface PromptTableItem extends PromptTemplateSchemeItem {
  categoryName: string
  templateSceneLabel: string
  effectiveSourceLabel: string
  configured: boolean
  groupDescription: string
}

const route = useRoute()
const router = useRouter()

const scmOptions = ref<ScmConfigItem[]>([])
const scmLoading = ref(false)
const selectedScmConfigId = ref(String(route.query.scmConfigId || ''))
const catalogItems = ref<PromptCatalogCategoryItem[]>([])
const allRows = ref<PromptTableItem[]>([])
const editorVisible = ref(false)
const editorReadonly = ref(true)
const editorLoading = ref(false)
const editingItem = ref<PromptTableItem>()
const currentScheme = ref<PromptTemplateSchemeItem | null>(null)
const effectiveScheme = ref<PromptTemplateSchemeItem | null>(null)

const currentMode = computed<'GLOBAL' | 'SCM'>(() => (selectedScmConfigId.value ? 'SCM' : 'GLOBAL'))

const categoryNameMap = computed(() => {
  const map: Record<string, string> = {}
  catalogItems.value.forEach((category) => {
    map[category.category] = category.categoryName
  })
  return map
})

const selectedScmOption = computed(
  () => scmOptions.value.find((item) => String(item.id) === selectedScmConfigId.value) || null
)

const modeAlertTitle = computed(() => {
  if (currentMode.value === 'GLOBAL') {
    return '当前正在维护系统全局兜底 Prompt。未选择仓库时，运行时将直接使用这里的模板。'
  }
  return '当前正在维护仓库级 Prompt 覆盖。若仓库未配置覆盖，将自动回退到系统全局兜底模板。'
})

const searchSchema = reactive<FormSchema[]>([
  {
    field: 'category',
    label: '一级分类',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: []
    }
  },
  {
    field: 'effectiveScope',
    label: '当前生效',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: [
        { label: '系统全局兜底', value: 'GLOBAL' },
        { label: '仓库级覆盖', value: 'SCM' }
      ]
    }
  },
  {
    field: 'keyword',
    label: '关键字',
    component: 'Input',
    componentProps: {
      placeholder: '模板名称 / 模板编码',
      maxlength: 100
    }
  }
])

const searchParams = ref<{
  category: string
  effectiveScope: EffectiveScopeFilter
  keyword: string
}>({
  category: '',
  effectiveScope: '',
  keyword: ''
})

const updateCategorySearchOptions = () => {
  const categorySchema = searchSchema.find((item) => item.field === 'category')
  if (!categorySchema) return
  categorySchema.componentProps = {
    ...(categorySchema.componentProps || {}),
    options: catalogItems.value.map((item) => ({
      label: item.categoryName,
      value: item.category
    }))
  }
}

const syncRouteQuery = () => {
  router.replace({
    path: '/rule-governance/prompt-templates',
    query: {
      ...(selectedScmConfigId.value ? { scmConfigId: selectedScmConfigId.value } : {})
    }
  })
}

const templateSceneLabelMap: Record<string, string> = {
  MAIN: '主模板',
  REPAIR: '结果修复',
  OTHER: '其他'
}

const toPromptQuery = (): PromptTemplateQueryParams => ({
  category: searchParams.value.category || undefined,
  keyword: searchParams.value.keyword || undefined
})

const buildPromptRow = (item: PromptTemplateSchemeItem): PromptTableItem => {
  const effectiveSourceLabel = item.effectiveScope === 'SCM' ? '仓库级覆盖生效' : '系统全局兜底生效'
  const configured =
    currentMode.value === 'GLOBAL'
      ? Boolean(item.contentText?.trim())
      : Boolean(item.hasScmOverride)
  return {
    ...item,
    categoryName: categoryNameMap.value[item.category] || item.category,
    templateSceneLabel: templateSceneLabelMap[item.templateScene] || item.templateScene,
    effectiveSourceLabel,
    configured,
    groupDescription: item.description || '当前模板组暂无额外说明'
  }
}

const summaryItems = computed(() =>
  catalogItems.value.map((category) => {
    const rows = allRows.value.filter((item) => item.category === category.category)
    const configuredCount =
      currentMode.value === 'GLOBAL'
        ? rows.filter((item) => Boolean(item.contentText?.trim())).length
        : rows.filter((item) => item.hasScmOverride).length
    return {
      key: category.category,
      title: category.categoryName,
      groupCount: rows.length || category.templateCount || 0,
      configuredCount,
      fallbackCount: Math.max((rows.length || category.templateCount || 0) - configuredCount, 0)
    }
  })
)

const { tableRegister, tableState, tableMethods } = useTable({
  fetchDataApi: async () => {
    const query = toPromptQuery()
    const [catalogRes, schemeRes] = await Promise.all([
      getPromptCatalogApi(query),
      currentMode.value === 'GLOBAL'
        ? getPromptGlobalSchemesApi(query)
        : getPromptScmSchemesApi(selectedScmConfigId.value, query)
    ])
    catalogItems.value = catalogRes.data || []
    updateCategorySearchOptions()
    let rows = (schemeRes.data || []).map(buildPromptRow)
    if (searchParams.value.effectiveScope) {
      rows = rows.filter((item) => item.effectiveScope === searchParams.value.effectiveScope)
    }
    allRows.value = rows
    const { currentPage, pageSize } = tableState
    const start = (currentPage.value - 1) * pageSize.value
    const end = start + pageSize.value
    return {
      list: rows.slice(start, end),
      total: rows.length
    }
  }
})

const { dataList, loading, currentPage, pageSize, total } = tableState
const { getList } = tableMethods

const tableColumns = reactive<TableColumn[]>([
  {
    field: 'categoryName',
    label: '一级分类',
    width: 140
  },
  {
    field: 'templateName',
    label: '模板组',
    minWidth: 240,
    slots: {
      default: ({ row }: { row: PromptTableItem }) => (
        <div>
          <div class="font-600">{row.templateName}</div>
          <div class="mt-4px text-12px color-[var(--el-text-color-secondary)]">
            {row.templateCode}
          </div>
        </div>
      )
    }
  },
  {
    field: 'templateSceneLabel',
    label: '模板场景',
    width: 120
  },
  {
    field: 'groupDescription',
    label: '说明',
    minWidth: 280
  },
  {
    field: 'effectiveSourceLabel',
    label: '当前生效',
    width: 150,
    slots: {
      default: ({ row }: { row: PromptTableItem }) => (
        <ElTag effect="light" type={row.effectiveScope === 'SCM' ? 'success' : 'info'}>
          {row.effectiveSourceLabel}
        </ElTag>
      )
    }
  },
  {
    field: 'configured',
    label: '当前配置',
    width: 140,
    slots: {
      default: ({ row }: { row: PromptTableItem }) => (
        <ElTag effect="light" type={row.configured ? 'success' : 'warning'}>
          {currentMode.value === 'GLOBAL'
            ? row.configured
              ? '已配置'
              : '未配置'
            : row.hasScmOverride
              ? '已覆盖'
              : '未覆盖'}
        </ElTag>
      )
    }
  },
  {
    field: 'updateTime',
    label: '最近更新时间',
    width: 180,
    slots: {
      default: ({ row }: { row: PromptTableItem }) => <span>{row.updateTime || '-'}</span>
    }
  },
  {
    field: 'action',
    label: '操作',
    width: 320,
    fixed: 'right',
    slots: {
      default: ({ row }: { row: PromptTableItem }) => (
        <div class="flex flex-wrap gap-8px">
          <BaseButton type="success" icon={View} onClick={() => openEditor(row, true)}>
            查看
          </BaseButton>
          {hasPermi('prompt-update') ? (
            <BaseButton
              type="primary"
              icon={row.hasScmOverride || currentMode.value === 'GLOBAL' ? Edit : Plus}
              onClick={() => openEditor(row, false)}
            >
              {currentMode.value === 'GLOBAL'
                ? '编辑全局'
                : row.hasScmOverride
                  ? '编辑覆盖'
                  : '新增覆盖'}
            </BaseButton>
          ) : null}
          {currentMode.value === 'SCM' && row.hasScmOverride && hasPermi('prompt-update') ? (
            <BaseButton type="danger" icon={RefreshLeft} onClick={() => restoreGlobal(row)}>
              恢复全局
            </BaseButton>
          ) : null}
        </div>
      )
    }
  }
])

const loadScmOptions = async () => {
  scmLoading.value = true
  try {
    const res = await listScmConfigsApi()
    scmOptions.value = (res.data || []).filter((item) => item.enabled !== false)
  } finally {
    scmLoading.value = false
  }
}

const refreshList = async () => {
  currentPage.value = 1
  await getList()
}

const setSearchParams = (params: Record<string, unknown>) => {
  searchParams.value = {
    category: String(params.category || ''),
    effectiveScope: String(params.effectiveScope || '') as EffectiveScopeFilter,
    keyword: String(params.keyword || '')
  }
  refreshList()
}

const handleScmChange = async (value: string) => {
  selectedScmConfigId.value = value
  syncRouteQuery()
  await refreshList()
}

const openCreateDialog = async () => {
  if (!selectedScmConfigId.value) {
    ElMessage.warning('请先选择仓库，再新增仓库级 Prompt 覆盖')
    return
  }
  const candidate = allRows.value.find((item) => !item.hasScmOverride) || allRows.value[0]
  if (!candidate) return
  await openEditor(candidate, false)
}

const openEditor = async (item: PromptTableItem, readonly: boolean) => {
  editorLoading.value = true
  try {
    const [detailRes, effectiveRes] = await Promise.all([
      getPromptTemplateDetailApi(
        item.templateCode,
        currentMode.value,
        currentMode.value === 'SCM' ? selectedScmConfigId.value : undefined
      ),
      getPromptEffectiveTemplateApi(
        item.templateCode,
        currentMode.value === 'SCM' ? selectedScmConfigId.value : undefined
      )
    ])
    editingItem.value = item
    currentScheme.value = detailRes.data || null
    effectiveScheme.value = effectiveRes.data || null
    editorReadonly.value = readonly
    editorVisible.value = true
  } finally {
    editorLoading.value = false
  }
}

const saveCurrentTemplate = async (payload: { contentText: string; remark: string }) => {
  if (!editingItem.value) return
  const requestPayload: PromptTemplateSavePayload = {
    templateCode: editingItem.value.templateCode,
    scope: currentMode.value,
    scmConfigId: currentMode.value === 'SCM' ? Number(selectedScmConfigId.value) : 0,
    contentText: payload.contentText,
    remark: payload.remark,
    status: 'ACTIVE'
  }
  editorLoading.value = true
  try {
    const res =
      currentMode.value === 'GLOBAL'
        ? await savePromptGlobalSchemeApi(editingItem.value.templateCode, requestPayload)
        : await savePromptScmSchemeApi(
            selectedScmConfigId.value,
            editingItem.value.templateCode,
            requestPayload
          )
    ElMessage.success(res.message || '提示词模板保存成功')
    editorVisible.value = false
    await refreshList()
  } finally {
    editorLoading.value = false
  }
}

const restoreGlobal = async (row: PromptTableItem) => {
  if (!selectedScmConfigId.value) return
  await ElMessageBox.confirm(
    `恢复后，仓库 ${selectedScmOption.value?.projectName || selectedScmConfigId.value} 将重新使用系统全局兜底模板。`,
    '确认恢复全局',
    {
      type: 'warning'
    }
  )
  await deletePromptScmOverrideApi(selectedScmConfigId.value, row.templateCode)
  ElMessage.success('仓库级 Prompt 覆盖已删除，当前模板重新走系统全局兜底')
  await refreshList()
}

onMounted(async () => {
  await loadScmOptions()
  syncRouteQuery()
  await refreshList()
})
</script>

<template>
  <ContentWrap>
    <ElAlert class="mb-12px" type="info" show-icon :closable="false" :title="modeAlertTitle" />

    <RuleScmContextBar
      :model-value="selectedScmConfigId"
      :scm-options="scmOptions"
      :loading="scmLoading"
      hint="不选择仓库时维护系统全局兜底；选择仓库后维护该仓库的 Prompt 覆盖。"
      @update:model-value="handleScmChange"
    />

    <PromptCategorySummary :items="summaryItems" :mode="currentMode" />

    <Search :schema="searchSchema" @reset="setSearchParams" @search="setSearchParams" />

    <div class="mb-10px flex gap-10px">
      <BaseButton :icon="Refresh" :loading="loading || scmLoading" @click="refreshList">
        刷新
      </BaseButton>
      <BaseButton
        v-if="hasPermi('prompt-update') && currentMode === 'SCM'"
        type="primary"
        :icon="Plus"
        @click="openCreateDialog"
      >
        新增仓库级覆盖
      </BaseButton>
    </div>

    <Table
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :columns="tableColumns"
      :data="dataList"
      :loading="loading || scmLoading"
      :pagination="{ total }"
      @register="tableRegister"
    />

    <PromptTemplateEditorDialog
      v-model="editorVisible"
      :item="editingItem"
      :current-scheme="currentScheme"
      :effective-scheme="effectiveScheme"
      :readonly="editorReadonly || !hasPermi('prompt-update')"
      :loading="editorLoading"
      @save="saveCurrentTemplate"
    />
  </ContentWrap>
</template>
