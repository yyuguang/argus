<template>
  <div>
    <Table :columns="columns" :data="rows" :loading="loading" empty-text="暂无接口日志质量问题" />
  </div>
</template>

<script setup lang="tsx">
import { BaseButton } from '@/components/Button'
import { Table, TableColumn } from '@/components/Table'
import { ElTag } from 'element-plus'

const props = defineProps<{
  rows: Array<Record<string, any>>
  loading?: boolean
  canIgnore?: boolean
}>()

const emit = defineEmits<{
  (event: 'ignore', row: Record<string, any>): void
}>()

const columns: TableColumn[] = [
  {
    field: 'appName',
    label: '应用 / 表',
    minWidth: 230,
    fixed: 'left',
    slots: {
      default: ({ row }) => (
        <div>
          <div class="cell-main">{row.appName || '-'}</div>
          <div class="cell-sub">{row.displayTableName}</div>
        </div>
      )
    }
  },
  {
    field: 'displayIssueType',
    label: '问题类型',
    minWidth: 180,
    slots: {
      default: ({ row }) => (
        <ElTag type={row.displayIssueTagType} effect="light">
          {row.displayIssueType}
        </ElTag>
      )
    }
  },
  {
    field: 'displayEmptyResponseRate',
    label: '质量指标',
    minWidth: 220,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="cell-main">空响应 {row.displayEmptyResponseRate}</div>
          <div class="cell-sub">缺 requestId {row.displayMissingRequestIdRate}</div>
        </div>
      )
    }
  },
  {
    field: 'displaySummary',
    label: '说明',
    minWidth: 320,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="cell-main">{row.displaySummary}</div>
          <div class="cell-sub">{row.displaySuggestion}</div>
        </div>
      )
    }
  }
]

if (props.canIgnore) {
  columns.push({
    field: 'action',
    label: '操作',
    fixed: 'right',
    width: 120,
    slots: {
      default: ({ row }) => (
        <div class="flex flex-wrap gap-8px">
          <BaseButton type="warning" onClick={() => emit('ignore', row)}>
            忽略
          </BaseButton>
        </div>
      )
    }
  })
}
</script>

<style scoped>
.cell-main {
  font-weight: 700;
  line-height: 1.45;
}

.cell-sub {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}
</style>
