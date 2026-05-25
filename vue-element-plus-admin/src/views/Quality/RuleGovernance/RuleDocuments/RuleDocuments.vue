<script setup lang="tsx">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
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
  activateRuleDocumentApi,
  disableRuleDocumentApi,
  getRuleDocumentsPageApi,
  reindexRuleDocumentApi
} from '@/api/rule'
import type { RuleDocumentItem, RuleDocumentPageParams } from '@/api/rule/types'
import { ElAlert, ElMessage, ElMessageBox, ElTag } from 'element-plus'
import { Plus, Refresh, Search as SearchIcon, View } from '@element-plus/icons-vue'
import RuleDocumentScopeTag from './components/RuleDocumentScopeTag.vue'
import RuleDocumentImportDialog from './components/RuleDocumentImportDialog.vue'
import RuleDocumentDetailDialog from './components/RuleDocumentDetailDialog.vue'

const route = useRoute()

const categoryOptions = [
  { label: '编码规范', value: 'CODING' },
  { label: '接口规范', value: 'API' },
  { label: '数据库规范', value: 'DATABASE' },
  { label: '安全规范', value: 'SECURITY' },
  { label: '自定义规范', value: 'CUSTOM' }
]

const scopeOptions = [
  { label: '全局', value: 'GLOBAL' },
  { label: '仓库级', value: 'SCM' }
]

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
  { label: '归档', value: 'ARCHIVED' }
]

const processStatusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' }
]

const searchSchema = reactive<FormSchema[]>([
  {
    field: 'category',
    label: '分类',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: categoryOptions
    }
  },
  {
    field: 'scope',
    label: '作用域',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: scopeOptions
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
    field: 'status',
    label: '文档状态',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: statusOptions
    }
  },
  {
    field: 'parseStatus',
    label: '解析状态',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: processStatusOptions
    }
  },
  {
    field: 'vectorStatus',
    label: '向量状态',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: processStatusOptions
    }
  },
  {
    field: 'keyword',
    label: '关键字',
    component: 'Input',
    componentProps: {
      maxlength: 100,
      placeholder: '文档名称 / 文档编码 / 文件名'
    }
  }
])

const scmLoading = ref(false)
const scmOptions = ref<ScmConfigItem[]>([])
const importVisible = ref(false)
const detailVisible = ref(false)
const currentDocumentId = ref<string | number>()
const searchParams = ref<RuleDocumentPageParams>({
  scmConfigId: String(route.query.scmConfigId || '') || undefined
})

const updateScmSearchOptions = () => {
  const schema = searchSchema.find((item) => item.field === 'scmConfigId')
  if (!schema) return
  schema.componentProps = {
    ...(schema.componentProps || {}),
    options: scmOptions.value.map((item) => ({
      label:
        item.repoOwner && item.repoName
          ? `${item.repoOwner}/${item.repoName}`
          : item.projectName || String(item.id),
      value: String(item.id)
    }))
  }
}

const { tableRegister, tableState, tableMethods } = useTable({
  fetchDataApi: async () => {
    const { currentPage, pageSize } = tableState
    const res = await getRuleDocumentsPageApi({
      pageNo: currentPage.value,
      pageSize: pageSize.value,
      ...searchParams.value
    })
    return {
      list: res.data.list || [],
      total: res.data.total || 0
    }
  }
})
const { dataList, loading, currentPage, pageSize, total } = tableState
const { getList } = tableMethods

const scopedScmNotice = computed(() =>
  searchParams.value.scmConfigId
    ? '当前已带入仓库上下文，可直接筛选和导入仓库级规范。'
    : '规范文档支持全局与仓库级两种作用域，评审时会按“全局 + 仓库级”共同检索。'
)

const tagType = (value?: string) => {
  if (value === 'ACTIVE' || value === 'SUCCESS') return 'success'
  if (value === 'FAILED' || value === 'DISABLED') return 'danger'
  if (value === 'PENDING' || value === 'DRAFT') return 'warning'
  return 'info'
}

const tableColumns = reactive<TableColumn[]>([
  { field: 'index', label: '序号', type: 'index' },
  {
    field: 'documentName',
    label: '文档',
    minWidth: 220,
    slots: {
      default: ({ row }: { row: RuleDocumentItem }) => (
        <div>
          <div class="font-600">{row.documentName || '-'}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            {row.documentCode || '-'}
          </div>
        </div>
      )
    }
  },
  {
    field: 'category',
    label: '分类',
    width: 120
  },
  {
    field: 'scope',
    label: '作用域',
    minWidth: 160,
    slots: {
      default: ({ row }: { row: RuleDocumentItem }) => (
        <RuleDocumentScopeTag scope={row.scope} scmProjectName={row.scmProjectName} />
      )
    }
  },
  {
    field: 'chunkCount',
    label: '分块数',
    width: 90
  },
  {
    field: 'status',
    label: '文档状态',
    width: 120,
    slots: {
      default: ({ row }: { row: RuleDocumentItem }) => (
        <ElTag effect="light" type={tagType(row.status)}>
          {row.status || '-'}
        </ElTag>
      )
    }
  },
  {
    field: 'parseStatus',
    label: '解析状态',
    width: 120,
    slots: {
      default: ({ row }: { row: RuleDocumentItem }) => (
        <ElTag effect="light" type={tagType(row.parseStatus)}>
          {row.parseStatus || '-'}
        </ElTag>
      )
    }
  },
  {
    field: 'vectorStatus',
    label: '向量状态',
    width: 120,
    slots: {
      default: ({ row }: { row: RuleDocumentItem }) => (
        <ElTag effect="light" type={tagType(row.vectorStatus)}>
          {row.vectorStatus || '-'}
        </ElTag>
      )
    }
  },
  {
    field: 'latestErrorMessage',
    label: '最近错误',
    minWidth: 220,
    slots: {
      default: ({ row }: { row: RuleDocumentItem }) => (
        <span class="text-12px color-[var(--el-text-color-secondary)]">
          {row.latestErrorMessage || '-'}
        </span>
      )
    }
  },
  {
    field: 'updateTime',
    label: '更新时间',
    width: 180
  },
  {
    field: 'action',
    label: '操作',
    width: 320,
    fixed: 'right',
    slots: {
      default: ({ row }: { row: RuleDocumentItem }) => (
        <div class="flex flex-wrap gap-8px">
          {hasPermi('view') ? (
            <BaseButton type="success" icon={View} onClick={() => openDetail(row)}>
              详情
            </BaseButton>
          ) : null}
          {hasPermi('activate') ? (
            <BaseButton
              type="primary"
              disabled={row.status === 'ACTIVE'}
              onClick={() => handleAction(row, 'activate')}
            >
              启用
            </BaseButton>
          ) : null}
          {hasPermi('disable') ? (
            <BaseButton
              type="danger"
              disabled={row.status === 'DISABLED'}
              onClick={() => handleAction(row, 'disable')}
            >
              停用
            </BaseButton>
          ) : null}
          {hasPermi('reindex') ? (
            <BaseButton icon={SearchIcon} onClick={() => handleAction(row, 'reindex')}>
              分块重建
            </BaseButton>
          ) : null}
        </div>
      )
    }
  }
])

const setSearchParams = (params: Record<string, unknown>) => {
  searchParams.value = {
    category: String(params.category || '') || undefined,
    scope: String(params.scope || '') || undefined,
    scmConfigId: String(params.scmConfigId || '') || undefined,
    status: String(params.status || '') || undefined,
    parseStatus: String(params.parseStatus || '') || undefined,
    vectorStatus: String(params.vectorStatus || '') || undefined,
    keyword: String(params.keyword || '') || undefined
  }
  currentPage.value = 1
  getList()
}

const loadScmOptions = async () => {
  scmLoading.value = true
  try {
    const res = await listScmConfigsApi()
    scmOptions.value = (res.data || []).filter((item) => item.enabled !== false)
    updateScmSearchOptions()
  } finally {
    scmLoading.value = false
  }
}

const refreshAll = () => {
  getList()
}

const openDetail = (row: RuleDocumentItem) => {
  currentDocumentId.value = row.id
  detailVisible.value = true
}

const handleAction = async (row: RuleDocumentItem, action: 'activate' | 'disable' | 'reindex') => {
  const actionMap = {
    activate: '启用',
    disable: '停用',
    reindex: '分块重建'
  }
  await ElMessageBox.confirm(
    `确认${actionMap[action]}文档「${row.documentName}」吗？`,
    '操作确认',
    {
      type: 'warning'
    }
  )
  if (!row.id) return
  const apiMap = {
    activate: activateRuleDocumentApi,
    disable: disableRuleDocumentApi,
    reindex: reindexRuleDocumentApi
  }
  const res = await apiMap[action](row.id)
  ElMessage.success(res.message || `${actionMap[action]}成功`)
  getList()
}

onMounted(async () => {
  await loadScmOptions()
  getList()
})
</script>

<template>
  <ContentWrap>
    <ElAlert class="mb-12px" type="info" show-icon :closable="false" :title="scopedScmNotice" />
    <Search :schema="searchSchema" @reset="setSearchParams" @search="setSearchParams" />
    <div class="mb-10px flex gap-10px">
      <BaseButton
        v-if="hasPermi('import')"
        type="primary"
        :icon="Plus"
        @click="importVisible = true"
      >
        导入规范
      </BaseButton>
      <BaseButton :icon="Refresh" :loading="loading || scmLoading" @click="refreshAll">
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

    <RuleDocumentImportDialog
      v-model="importVisible"
      :preset-scm-config-id="String(searchParams.scmConfigId || '') || undefined"
      :scm-options="scmOptions"
      @success="refreshAll"
    />
    <RuleDocumentDetailDialog v-model="detailVisible" :document-id="currentDocumentId" />
  </ContentWrap>
</template>
