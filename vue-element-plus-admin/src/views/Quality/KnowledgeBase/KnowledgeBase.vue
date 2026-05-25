<script setup lang="tsx">
import { ContentWrap } from '@/components/ContentWrap'
import { Search } from '@/components/Search'
import { Table, TableColumn } from '@/components/Table'
import { Dialog } from '@/components/Dialog'
import { BaseButton } from '@/components/Button'
import { hasPermi } from '@/components/Permission/src/utils'
import { FormSchema } from '@/components/Form'
import {
  confirmKnowledgeEntryApi,
  demoteKnowledgeWhitelistApi,
  getKnowledgeAuditApi,
  getKnowledgeEntriesApi,
  getKnowledgeEntryDetailApi,
  getKnowledgeHighFrequencyApi,
  getKnowledgeNewFingerprintsApi,
  getKnowledgeSurgingFingerprintsApi,
  getKnowledgeWhitelistCandidatesApi,
  ignoreKnowledgeEntryApi,
  markKnowledgeFalsePositiveApi,
  promoteKnowledgeWhitelistApi
} from '@/api/knowledge'
import type {
  KnowledgeAuditItem,
  KnowledgeEntryItem,
  KnowledgeSearchParams,
  KnowledgeSummaryItem
} from '@/api/knowledge/types'
import { useUserStore } from '@/store/modules/user'
import { computed, onMounted, ref } from 'vue'
import {
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElTabPane,
  ElTabs,
  ElTag
} from 'element-plus'
import { Check, CircleClose, Promotion, Refresh, Remove, View } from '@element-plus/icons-vue'

const userStore = useUserStore()

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '白名单', value: 'WHITELIST' },
  { label: '误报', value: 'FALSE_POSITIVE' },
  { label: '已过时', value: 'OUTDATED' }
]

const currentOperator = computed(() => userStore.getUserInfo?.username || 'system')

const searchSchema = ref<FormSchema[]>([
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
    field: 'errorType',
    label: '错误类型',
    component: 'Input',
    componentProps: {
      placeholder: '例如 NULL_POINTER'
    }
  },
  {
    field: 'appName',
    label: '应用',
    component: 'Input',
    componentProps: {
      placeholder: '例如 order-service'
    }
  }
])

const loading = ref(false)
const entries = ref<KnowledgeEntryItem[]>([])
const candidateLoading = ref(false)
const whitelistCandidates = ref<KnowledgeEntryItem[]>([])
const highFrequency = ref<KnowledgeSummaryItem[]>([])
const newFingerprints = ref<KnowledgeSummaryItem[]>([])
const surgingFingerprints = ref<KnowledgeSummaryItem[]>([])
const searchParams = ref<KnowledgeSearchParams>({})

const detailVisible = ref(false)
const detailLoading = ref(false)
const activeDetailTab = ref('basic')
const currentEntry = ref<KnowledgeEntryItem>()
const auditLoading = ref(false)
const auditList = ref<KnowledgeAuditItem[]>([])

const formatDate = (value?: string) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const statusTagType = (status?: string) => {
  if (status === 'CONFIRMED') return 'success'
  if (status === 'WHITELIST') return 'warning'
  if (status === 'FALSE_POSITIVE') return 'danger'
  if (status === 'OUTDATED') return 'info'
  return undefined
}

const statusLabel = (status?: string) => {
  return statusOptions.find((item) => item.value === status)?.label || status || '-'
}

const summaryCards = computed(() => [
  {
    key: 'high',
    title: '高频指纹',
    value: highFrequency.value.length,
    hint: highFrequency.value[0]
      ? `${highFrequency.value[0].appName || '-'} / ${highFrequency.value[0].errorType || '-'}`
      : '暂无数据'
  },
  {
    key: 'new',
    title: '新增指纹',
    value: newFingerprints.value.length,
    hint: newFingerprints.value[0]
      ? `${newFingerprints.value[0].appName || '-'} / ${newFingerprints.value[0].errorType || '-'}`
      : '暂无数据'
  },
  {
    key: 'surging',
    title: '突增指纹',
    value: surgingFingerprints.value.length,
    hint: surgingFingerprints.value[0]
      ? `${surgingFingerprints.value[0].appName || '-'} / +${surgingFingerprints.value[0].increaseTotal || 0}`
      : '暂无数据'
  },
  {
    key: 'candidate',
    title: '白名单候选',
    value: whitelistCandidates.value.length,
    hint: whitelistCandidates.value[0]
      ? `${whitelistCandidates.value[0].title || whitelistCandidates.value[0].appName || '-'}`
      : '暂无数据'
  }
])

const parseTags = (value?: string) => {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const loadEntries = async () => {
  loading.value = true
  try {
    const res = await getKnowledgeEntriesApi(searchParams.value)
    entries.value = res.data || []
  } finally {
    loading.value = false
  }
}

const loadDashboard = async () => {
  candidateLoading.value = true
  try {
    const [candidateRes, highRes, newRes, surgingRes] = await Promise.all([
      getKnowledgeWhitelistCandidatesApi(5),
      getKnowledgeHighFrequencyApi(24, 5, 5),
      getKnowledgeNewFingerprintsApi(24, 5),
      getKnowledgeSurgingFingerprintsApi(24, 5, 5)
    ])
    whitelistCandidates.value = candidateRes.data || []
    highFrequency.value = highRes.data || []
    newFingerprints.value = newRes.data || []
    surgingFingerprints.value = surgingRes.data || []
  } finally {
    candidateLoading.value = false
  }
}

const reloadAll = async () => {
  await Promise.all([loadEntries(), loadDashboard()])
}

const setSearchParams = (params: Record<string, any>) => {
  searchParams.value = {
    status: params.status || undefined,
    errorType: params.errorType || undefined,
    appName: params.appName || undefined
  }
  loadEntries()
}

const openDetail = async (row: KnowledgeEntryItem) => {
  detailVisible.value = true
  activeDetailTab.value = 'basic'
  detailLoading.value = true
  auditLoading.value = true
  try {
    const [detailRes, auditRes] = await Promise.all([
      getKnowledgeEntryDetailApi(row.id!),
      getKnowledgeAuditApi(row.id!)
    ])
    currentEntry.value = detailRes.data
    auditList.value = auditRes.data || []
  } finally {
    detailLoading.value = false
    auditLoading.value = false
  }
}

const withCommentAction = async (
  row: KnowledgeEntryItem,
  title: string,
  action: (id: string | number, payload: { operator: string; comment?: string }) => Promise<any>,
  successText: string
) => {
  const result = await ElMessageBox.prompt('请输入操作备注', title, {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '可选，建议填写处理说明',
    inputValue: ''
  }).catch(() => null)
  if (!result || result.action !== 'confirm') {
    return
  }
  await action(row.id!, {
    operator: currentOperator.value,
    comment: result.value || undefined
  })
  ElMessage.success(successText)
  await reloadAll()
  if (detailVisible.value && currentEntry.value?.id === row.id) {
    await openDetail(row)
  }
}

const withSimpleAction = async (
  row: KnowledgeEntryItem,
  title: string,
  message: string,
  action: (id: string | number, operator: string) => Promise<any>,
  successText: string
) => {
  await ElMessageBox.confirm(message, title, {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
  await action(row.id!, currentOperator.value)
  ElMessage.success(successText)
  await reloadAll()
  if (detailVisible.value && currentEntry.value?.id === row.id) {
    await openDetail(row)
  }
}

const tableColumns = ref<TableColumn[]>([
  {
    field: 'title',
    label: '知识条目',
    minWidth: 300,
    slots: {
      default: ({ row }: { row: KnowledgeEntryItem }) => (
        <div>
          <div class="font-600">{row.title || '-'}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            {row.appName || '-'} / {row.errorType || '-'}
          </div>
        </div>
      )
    }
  },
  {
    field: 'status',
    label: '状态',
    width: 120,
    slots: {
      default: ({ row }: { row: KnowledgeEntryItem }) => (
        <ElTag type={statusTagType(row.status)}>{statusLabel(row.status)}</ElTag>
      )
    }
  },
  {
    field: 'occurrenceCount',
    label: '发生次数',
    width: 120
  },
  {
    field: 'source',
    label: '来源',
    width: 100
  },
  {
    field: 'confirmedBy',
    label: '确认人',
    width: 120,
    slots: {
      default: ({ row }: { row: KnowledgeEntryItem }) => <>{row.confirmedBy || '-'}</>
    }
  },
  {
    field: 'lastOccurredAt',
    label: '最近发生',
    width: 180,
    slots: {
      default: ({ row }: { row: KnowledgeEntryItem }) => <>{formatDate(row.lastOccurredAt)}</>
    }
  },
  {
    field: 'action',
    label: '操作',
    minWidth: 360,
    fixed: 'right',
    slots: {
      default: ({ row }: { row: KnowledgeEntryItem }) => (
        <div class="flex flex-wrap gap-8px">
          {hasPermi('view') ? (
            <BaseButton type="success" icon={View} onClick={() => openDetail(row)}>
              详情
            </BaseButton>
          ) : null}
          {hasPermi('confirm') && row.status !== 'CONFIRMED' && row.status !== 'WHITELIST' ? (
            <BaseButton
              type="primary"
              icon={Check}
              onClick={() =>
                withCommentAction(row, '确认知识条目', confirmKnowledgeEntryApi, '知识条目已确认')
              }
            >
              确认
            </BaseButton>
          ) : null}
          {hasPermi('false-positive') && row.status !== 'FALSE_POSITIVE' ? (
            <BaseButton
              type="danger"
              icon={CircleClose}
              onClick={() =>
                withCommentAction(
                  row,
                  '标记误报',
                  markKnowledgeFalsePositiveApi,
                  '知识条目已标记为误报'
                )
              }
            >
              误报
            </BaseButton>
          ) : null}
          {hasPermi('ignore') && row.status !== 'OUTDATED' ? (
            <BaseButton
              icon={Remove}
              onClick={() =>
                withCommentAction(row, '忽略知识条目', ignoreKnowledgeEntryApi, '知识条目已忽略')
              }
            >
              忽略
            </BaseButton>
          ) : null}
          {hasPermi('promote-whitelist') && row.status === 'CONFIRMED' ? (
            <BaseButton
              type="warning"
              icon={Promotion}
              onClick={() =>
                withSimpleAction(
                  row,
                  '提升白名单',
                  '确认将该条目提升为白名单吗？',
                  promoteKnowledgeWhitelistApi,
                  '知识条目已提升为白名单'
                )
              }
            >
              提升白名单
            </BaseButton>
          ) : null}
          {hasPermi('demote-whitelist') && row.status === 'WHITELIST' ? (
            <BaseButton
              type="warning"
              icon={Promotion}
              onClick={() =>
                withSimpleAction(
                  row,
                  '降级白名单',
                  '确认将该条目从白名单降级吗？',
                  demoteKnowledgeWhitelistApi,
                  '知识条目已从白名单降级'
                )
              }
            >
              降级白名单
            </BaseButton>
          ) : null}
        </div>
      )
    }
  }
])

const candidateColumns = ref<TableColumn[]>([
  {
    field: 'title',
    label: '白名单候选',
    minWidth: 260
  },
  {
    field: 'appName',
    label: '应用',
    width: 160
  },
  {
    field: 'errorType',
    label: '错误类型',
    width: 160
  },
  {
    field: 'occurrenceCount',
    label: '发生次数',
    width: 120
  },
  {
    field: 'action',
    label: '操作',
    width: 120,
    slots: {
      default: ({ row }: { row: KnowledgeEntryItem }) => (
        <div class="flex flex-wrap gap-8px">
          <BaseButton type="success" icon={View} onClick={() => openDetail(row)}>
            详情
          </BaseButton>
        </div>
      )
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

    <div class="summary-grid">
      <div v-for="item in summaryCards" :key="item.key" class="summary-card">
        <div class="summary-title">{{ item.title }}</div>
        <div class="summary-value">{{ item.value }}</div>
        <div class="summary-hint">{{ item.hint }}</div>
      </div>
    </div>

    <div class="mb-10px flex gap-10px">
      <BaseButton :icon="Refresh" :loading="loading || candidateLoading" @click="reloadAll">
        刷新
      </BaseButton>
    </div>

    <Table :columns="tableColumns" :data="entries" :loading="loading" />

    <div class="mt-16px">
      <div class="section-title">白名单候选</div>
      <Table :columns="candidateColumns" :data="whitelistCandidates" :loading="candidateLoading" />
    </div>
  </ContentWrap>

  <Dialog
    v-model="detailVisible"
    title="知识条目详情"
    width="920px"
    max-height="calc(100vh - 170px)"
  >
    <div v-loading="detailLoading">
      <template v-if="currentEntry">
        <ElTabs v-model="activeDetailTab">
          <ElTabPane label="基础信息" name="basic">
            <ElDescriptions :column="2" border>
              <ElDescriptionsItem label="标题">{{ currentEntry.title || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="状态">
                <ElTag :type="statusTagType(currentEntry.status)">
                  {{ statusLabel(currentEntry.status) }}
                </ElTag>
              </ElDescriptionsItem>
              <ElDescriptionsItem label="应用">{{
                currentEntry.appName || '-'
              }}</ElDescriptionsItem>
              <ElDescriptionsItem label="错误类型">{{
                currentEntry.errorType || '-'
              }}</ElDescriptionsItem>
              <ElDescriptionsItem label="发生次数">
                {{ currentEntry.occurrenceCount ?? 0 }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="最近发生">
                {{ formatDate(currentEntry.lastOccurredAt) }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="确认人">
                {{ currentEntry.confirmedBy || '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="确认时间">
                {{ formatDate(currentEntry.confirmedAt) }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="错误指纹" :span="2">
                <div class="detail-text">{{ currentEntry.errorFingerprint || '-' }}</div>
              </ElDescriptionsItem>
              <ElDescriptionsItem label="标签" :span="2">
                <div v-if="parseTags(currentEntry.tagsJson).length" class="tag-list">
                  <ElTag
                    v-for="tag in parseTags(currentEntry.tagsJson)"
                    :key="tag"
                    effect="plain"
                    class="mr-8px mb-8px"
                  >
                    {{ tag }}
                  </ElTag>
                </div>
                <span v-else>-</span>
              </ElDescriptionsItem>
            </ElDescriptions>
          </ElTabPane>

          <ElTabPane label="经验内容" name="content">
            <div class="detail-block">
              <div class="detail-label">错误模式</div>
              <div class="detail-text">{{ currentEntry.errorPattern || '-' }}</div>
            </div>
            <div class="detail-block">
              <div class="detail-label">根因分析</div>
              <div class="detail-text">{{ currentEntry.rootCause || '-' }}</div>
            </div>
            <div class="detail-block">
              <div class="detail-label">修复建议</div>
              <div class="detail-text">{{ currentEntry.fixSuggestion || '-' }}</div>
            </div>
            <div class="detail-block">
              <div class="detail-label">预防建议</div>
              <div class="detail-text">{{ currentEntry.preventionAdvice || '-' }}</div>
            </div>
          </ElTabPane>

          <ElTabPane label="操作留痕" name="audit">
            <div v-loading="auditLoading">
              <ElEmpty v-if="!auditList.length" description="暂无操作留痕" />
              <div v-else class="audit-list">
                <div v-for="item in auditList" :key="item.id" class="audit-item">
                  <div class="audit-header">
                    <strong>{{ item.action || '-' }}</strong>
                    <span>{{ formatDate(item.createTime) }}</span>
                  </div>
                  <div class="audit-meta">
                    操作人：{{ item.operator || '-' }} | {{ item.beforeStatus || '-' }} ->
                    {{ item.afterStatus || '-' }}
                  </div>
                  <div class="audit-comment">{{ item.comment || '无备注' }}</div>
                </div>
              </div>
            </div>
          </ElTabPane>
        </ElTabs>
      </template>
      <ElEmpty v-else description="暂无详情数据" />
    </div>

    <template #footer>
      <BaseButton @click="detailVisible = false">关闭</BaseButton>
    </template>
  </Dialog>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.summary-title {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.summary-value {
  margin-top: 8px;
  color: var(--el-text-color-primary);
  font-size: 28px;
  font-weight: 600;
}

.summary-hint {
  margin-top: 8px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.section-title {
  margin-bottom: 10px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.detail-block + .detail-block {
  margin-top: 16px;
}

.detail-label {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.detail-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
}

.audit-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.audit-item {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.audit-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.audit-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.audit-comment {
  margin-top: 8px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
