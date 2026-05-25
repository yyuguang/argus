<script setup lang="tsx">
import { ContentWrap } from '@/components/ContentWrap'
import { Dialog } from '@/components/Dialog'
import { Search } from '@/components/Search'
import { Table, TableColumn } from '@/components/Table'
import { BaseButton } from '@/components/Button'
import { hasPermi } from '@/components/Permission/src/utils'
import { FormSchema } from '@/components/Form'
import { useTable } from '@/hooks/web/useTable'
import { getReviewTaskDetailApi, getReviewTaskPageApi } from '@/api/review'
import type { ReviewIssueItem, ReviewTaskItem } from '@/api/review/types'
import { reactive, ref, unref } from 'vue'
import {
  ElAlert,
  ElSkeleton,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTimeline,
  ElTimelineItem
} from 'element-plus'
import { Refresh, View } from '@element-plus/icons-vue'

const providerOptions = [
  { label: 'GitLab', value: 'gitlab' },
  { label: 'GitHub', value: 'github' },
  { label: 'Gitee', value: 'gitee' }
]

const statusOptions = ['PENDING', 'RUNNING', 'DONE', 'FAILED', 'TIMEOUT'].map((item) => ({
  label: item,
  value: item
}))

const providerTagType = (provider?: string) => {
  if (provider === 'gitlab') return 'warning'
  if (provider === 'github') return 'info'
  if (provider === 'gitee') return 'danger'
  return 'primary'
}

const statusTagType = (status?: string) => {
  if (status === 'DONE') return 'success'
  if (status === 'RUNNING') return 'primary'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  return 'warning'
}

const issueSeverityType = (severity?: string) => {
  if (severity === 'CRITICAL') return 'danger'
  if (severity === 'MAJOR') return 'warning'
  if (severity === 'MINOR') return 'primary'
  return 'success'
}

const formatDate = (value?: string) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const formatDuration = (value?: number) => {
  if (value === undefined || value === null) return '-'
  if (value < 1000) return `${value}ms`
  return `${(value / 1000).toFixed(1)}s`
}

const composeRepo = (row: ReviewTaskItem) => {
  if (row.repoOwner && row.repoName) {
    return `${row.repoOwner}/${row.repoName}`
  }
  return row.projectName || row.repoName || '-'
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
    field: 'status',
    label: '任务状态',
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
      placeholder: '仓库名 / 标题 / 分支',
      maxlength: 100
    }
  }
])

const searchParams = ref<Record<string, any>>({})
const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedTask = ref<ReviewTaskItem>()
const selectedIssues = ref<ReviewIssueItem[]>([])
const activeDetailTab = ref('basic')

const { tableRegister, tableState, tableMethods } = useTable({
  fetchDataApi: async () => {
    const { pageSize, currentPage } = tableState
    const res = await getReviewTaskPageApi({
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

const tableColumns = reactive<TableColumn[]>([
  {
    field: 'index',
    label: '序号',
    type: 'index'
  },
  {
    field: 'id',
    label: '任务 ID',
    width: 100
  },
  {
    field: 'scmProvider',
    label: '平台',
    width: 110,
    slots: {
      default: ({ row }: { row: ReviewTaskItem }) => (
        <ElTag type={providerTagType(row.scmProvider)} effect="light">
          {row.scmProvider || '-'}
        </ElTag>
      )
    }
  },
  {
    field: 'projectName',
    label: '仓库 / 标题',
    minWidth: 320,
    slots: {
      default: ({ row }: { row: ReviewTaskItem }) => (
        <div>
          <div class="font-600">{row.projectName || composeRepo(row)}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">{row.mrTitle || '-'}</div>
        </div>
      )
    }
  },
  {
    field: 'authorName',
    label: '提交者',
    minWidth: 120,
    slots: {
      default: ({ row }: { row: ReviewTaskItem }) => <>{row.authorName || '-'}</>
    }
  },
  {
    field: 'branch',
    label: '分支',
    minWidth: 190,
    slots: {
      default: ({ row }: { row: ReviewTaskItem }) => (
        <div>
          <div>{row.sourceBranch || '-'}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            → {row.targetBranch || '-'}
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
      default: ({ row }: { row: ReviewTaskItem }) => (
        <ElTag type={statusTagType(row.status)}>{row.status || '-'}</ElTag>
      )
    }
  },
  {
    field: 'score',
    label: '评分',
    width: 110,
    slots: {
      default: ({ row }: { row: ReviewTaskItem }) => (
        <div>
          <div>{row.totalScore ?? '-'}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            {row.scoreLevel || '-'}
          </div>
        </div>
      )
    }
  },
  {
    field: 'issueCount',
    label: '问题统计',
    width: 150,
    slots: {
      default: ({ row }: { row: ReviewTaskItem }) => (
        <div class="text-12px">
          <div>致命 {row.criticalCount || 0}</div>
          <div>
            严重 {row.majorCount || 0} / 建议 {row.minorCount || 0}
          </div>
        </div>
      )
    }
  },
  {
    field: 'updateTime',
    label: '更新时间',
    width: 180,
    slots: {
      default: ({ row }: { row: ReviewTaskItem }) => (
        <>{formatDate(row.updateTime || row.createTime)}</>
      )
    }
  },
  {
    field: 'action',
    label: '操作',
    width: 120,
    fixed: 'right',
    slots: {
      default: ({ row }: { row: ReviewTaskItem }) => (
        <div class="flex flex-wrap gap-8px">
          {hasPermi('view') ? (
            <BaseButton type="primary" icon={View} onClick={() => openDetail(row)}>
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

const openDetail = async (row: ReviewTaskItem) => {
  detailVisible.value = true
  detailLoading.value = true
  activeDetailTab.value = 'basic'
  selectedTask.value = row
  selectedIssues.value = []
  try {
    const res = await getReviewTaskDetailApi(row.id || '')
    selectedTask.value = res.data.task || row
    selectedIssues.value = res.data.issues || []
  } finally {
    detailLoading.value = false
  }
}
</script>

<template>
  <ContentWrap>
    <Search :schema="searchSchema" @reset="setSearchParams" @search="setSearchParams" />
    <div class="mb-10px">
      <BaseButton :icon="Refresh" :loading="loading" @click="getList">刷新</BaseButton>
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

  <Dialog
    v-model="detailVisible"
    title="评审任务详情"
    width="920px"
    max-height="calc(100vh - 170px)"
  >
    <ElSkeleton v-if="detailLoading" :rows="8" animated />
    <template v-else-if="selectedTask">
      <ElTabs v-model="activeDetailTab">
        <ElTabPane label="基础信息" name="basic">
          <div class="task-detail-grid">
            <div>
              <span>任务 ID</span><strong>{{ selectedTask.id }}</strong>
            </div>
            <div>
              <span>平台</span><strong>{{ selectedTask.scmProvider || '-' }}</strong>
            </div>
            <div>
              <span>仓库</span
              ><strong>{{ selectedTask.projectName || composeRepo(selectedTask) }}</strong>
            </div>
            <div>
              <span>提交者</span><strong>{{ selectedTask.authorName || '-' }}</strong>
            </div>
            <div>
              <span>状态</span><strong>{{ selectedTask.status || '-' }}</strong>
            </div>
            <div>
              <span>评分</span
              ><strong
                >{{ selectedTask.totalScore ?? '-' }} / {{ selectedTask.scoreLevel || '-' }}</strong
              >
            </div>
            <div>
              <span>分支</span
              ><strong
                >{{ selectedTask.sourceBranch || '-' }} →
                {{ selectedTask.targetBranch || '-' }}</strong
              >
            </div>
            <div>
              <span>耗时</span><strong>{{ formatDuration(selectedTask.duration) }}</strong>
            </div>
            <div>
              <span>提交链接</span><strong>{{ selectedTask.mrUrl || '-' }}</strong>
            </div>
            <div>
              <span>最近更新时间</span
              ><strong>{{ formatDate(selectedTask.updateTime || selectedTask.createTime) }}</strong>
            </div>
          </div>

          <ElAlert
            v-if="selectedTask.errorMessage"
            class="mt-14px"
            :title="selectedTask.errorMessage"
            type="error"
            show-icon
            :closable="false"
          />
        </ElTabPane>

        <ElTabPane label="评审总结" name="summary">
          <div class="summary-box">{{ selectedTask.summary || '暂无总结' }}</div>
        </ElTabPane>

        <ElTabPane :label="`问题明细 (${selectedIssues.length})`" name="issues">
          <ElAlert
            v-if="!selectedIssues.length"
            title="当前任务暂无问题明细"
            type="info"
            show-icon
            :closable="false"
          />
          <ElTimeline v-else>
            <ElTimelineItem
              v-for="issue in selectedIssues"
              :key="issue.id"
              :type="issueSeverityType(issue.severity)"
              :timestamp="`${issue.filePath || '-'} : ${issue.startLine || '-'}-${issue.endLine || '-'} 行`"
            >
              <div class="issue-item">
                <strong>{{ issue.severity || '-' }} / {{ issue.category || '-' }}</strong>
                <p>{{ issue.description || '-' }}</p>
                <p class="suggestion">建议：{{ issue.suggestion || '暂无' }}</p>
                <pre v-if="issue.codeSnippet">{{ issue.codeSnippet }}</pre>
              </div>
            </ElTimelineItem>
          </ElTimeline>
        </ElTabPane>
      </ElTabs>
    </template>
    <template #footer>
      <BaseButton @click="detailVisible = false">关闭</BaseButton>
    </template>
  </Dialog>
</template>

<style scoped>
.task-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.task-detail-grid div {
  min-width: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.task-detail-grid span {
  display: block;
  margin-bottom: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.task-detail-grid strong {
  display: block;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.summary-box {
  min-height: 240px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  white-space: pre-wrap;
}

.issue-item {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.issue-item p {
  margin: 8px 0 0;
}

.issue-item .suggestion {
  color: var(--el-text-color-secondary);
}

.issue-item pre {
  max-height: 220px;
  margin: 10px 0 0;
  padding: 10px;
  overflow: auto;
  border-radius: 6px;
  background: var(--el-fill-color-darker);
  font-size: 12px;
}
</style>
