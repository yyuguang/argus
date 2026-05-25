<template>
  <div>
    <Table :columns="columns" :data="rows" :loading="loading" empty-text="暂无连接池风险" />
  </div>
</template>

<script setup lang="tsx">
import { Table, TableColumn } from '@/components/Table'
import { ElTag } from 'element-plus'

const columns: TableColumn[] = [
  {
    field: 'appName',
    label: '应用 / 实例',
    minWidth: 210,
    fixed: 'left',
    slots: {
      default: ({ row }) => (
        <div>
          <div class="cell-main">{row.appName || '-'}</div>
          <div class="cell-sub">{row.displayPoolName}</div>
        </div>
      )
    }
  },
  {
    field: 'poolType',
    label: '连接池类型',
    width: 140,
    slots: {
      default: ({ row }) => <ElTag effect="light">{row.poolType || '-'}</ElTag>
    }
  },
  {
    field: 'displayActiveConnections',
    label: '活跃 / 最大',
    minWidth: 150,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="cell-main">
            {row.displayActiveConnections} / {row.displayMaxConnections}
          </div>
          <div class="cell-sub">使用率 {row.displayUsagePercent}</div>
        </div>
      )
    }
  },
  {
    field: 'displayWaitingThreads',
    label: '等待 / 超时',
    minWidth: 150,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="cell-main">{row.displayWaitingThreads} waiting</div>
          <div class="cell-sub">{row.displayTimeoutCount} timeout</div>
        </div>
      )
    }
  },
  {
    field: 'displayRiskLevel',
    label: '风险',
    minWidth: 280,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="cell-main">{row.displayRiskLevel}</div>
          <div class="cell-sub">{row.displayRiskReason}</div>
        </div>
      )
    }
  }
]

defineProps<{
  rows: Array<Record<string, any>>
  loading?: boolean
}>()
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
