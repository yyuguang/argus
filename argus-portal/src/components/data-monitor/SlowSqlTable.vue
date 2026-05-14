<template>
  <div>
    <el-table :data="rows" v-loading="loading" border @row-click="$emit('detail', $event)">
      <el-table-column label="应用 / 数据源" min-width="190" fixed>
        <template #default="{ row }">
          <div class="cell-main">{{ row.appName || '-' }}</div>
          <div class="cell-sub">{{ row.displayDatasource }}</div>
        </template>
      </el-table-column>
      <el-table-column label="耗时 / QPS" min-width="160">
        <template #default="{ row }">
          <div class="cell-main">{{ row.displayDuration }}</div>
          <div class="cell-sub">QPS {{ row.displayQps }}</div>
        </template>
      </el-table-column>
      <el-table-column label="锁与事务" min-width="170">
        <template #default="{ row }">
          <el-tag :type="row.lockRisk ? 'danger' : 'info'" effect="light">{{ row.displayLockLabel }}</el-tag>
          <div class="cell-sub">{{ row.displayLockSummary }}</div>
        </template>
      </el-table-column>
      <el-table-column label="SQL 摘要" min-width="320">
        <template #default="{ row }">
          <div class="sql-preview">{{ row.displaySqlPreview }}</div>
        </template>
      </el-table-column>
      <el-table-column label="索引建议" min-width="240">
        <template #default="{ row }">
          <div class="cell-main">{{ row.displayIndexSuggestion }}</div>
          <div class="cell-sub">仅展示，不自动执行</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.displayStatusType" effect="light">{{ row.displayStatusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="180">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click.stop="$emit('detail', row)">详情</el-button>
          <el-button link type="warning" @click.stop="$emit('ignore', row)">忽略</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无慢 SQL 事件" />
  </div>
</template>

<script setup>
import { View } from '@element-plus/icons-vue'

defineProps({
  rows: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['detail', 'ignore'])
</script>

<style scoped>
.cell-main {
  font-weight: 700;
  color: var(--text);
  line-height: 1.45;
}

.cell-sub {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}

.sql-preview {
  max-width: 520px;
  color: var(--text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>

