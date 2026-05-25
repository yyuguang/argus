<template>
  <div>
    <Table
      :columns="columns"
      :data="rows"
      :loading="loading"
      empty-text="暂无慢 SQL 事件"
      @row-click="$emit('detail', $event)"
    />
  </div>
</template>

<script setup lang="tsx">
import { BaseButton } from '@/components/Button'
import { Table, TableColumn } from '@/components/Table'
import { View } from '@element-plus/icons-vue'
import { ElTag } from 'element-plus'

const props = defineProps<{
  rows: Array<Record<string, any>>
  loading?: boolean
  canConfirm?: boolean
  canIgnore?: boolean
}>()

const emit = defineEmits<{
  (event: 'detail', row: Record<string, any>): void
  (event: 'confirm', row: Record<string, any>): void
  (event: 'ignore', row: Record<string, any>): void
}>()

const columns: TableColumn[] = [
  {
    field: 'appName',
    label: '应用 / 数据源',
    minWidth: 190,
    fixed: 'left',
    slots: {
      default: ({ row }) => (
        <div>
          <div class="cell-main">{row.appName || '-'}</div>
          <div class="cell-sub">{row.displayDatasource}</div>
        </div>
      )
    }
  },
  {
    field: 'displayDuration',
    label: '耗时 / QPS',
    minWidth: 160,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="cell-main">{row.displayDuration}</div>
          <div class="cell-sub">QPS {row.displayQps}</div>
        </div>
      )
    }
  },
  {
    field: 'displayLockLabel',
    label: '锁与事务',
    minWidth: 170,
    slots: {
      default: ({ row }) => (
        <div>
          <ElTag type={row.lockRisk ? 'danger' : 'info'} effect="light">
            {row.displayLockLabel}
          </ElTag>
          <div class="cell-sub">{row.displayLockSummary}</div>
        </div>
      )
    }
  },
  {
    field: 'displaySqlPreview',
    label: 'SQL 摘要',
    minWidth: 320,
    slots: {
      default: ({ row }) => <div class="sql-preview">{row.displaySqlPreview}</div>
    }
  },
  {
    field: 'displayIndexSuggestion',
    label: '索引建议',
    minWidth: 240,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="cell-main">{row.displayIndexSuggestion}</div>
          <div class="cell-sub">仅展示，不自动执行</div>
        </div>
      )
    }
  },
  {
    field: 'displayStatusLabel',
    label: '状态',
    width: 120,
    slots: {
      default: ({ row }) => (
        <ElTag type={row.displayStatusType} effect="light">
          {row.displayStatusLabel}
        </ElTag>
      )
    }
  },
  {
    field: 'action',
    label: '操作',
    fixed: 'right',
    width: 220,
    slots: {
      default: ({ row }) => (
        <div class="flex flex-wrap gap-8px">
          <BaseButton type="primary" icon={View} onClick={() => emit('detail', row)}>
            详情
          </BaseButton>
          {props.canConfirm ? (
            <BaseButton type="success" onClick={() => emit('confirm', row)}>
              确认
            </BaseButton>
          ) : null}
          {props.canIgnore ? (
            <BaseButton type="warning" onClick={() => emit('ignore', row)}>
              忽略
            </BaseButton>
          ) : null}
        </div>
      )
    }
  }
]
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

.sql-preview {
  max-width: 520px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
