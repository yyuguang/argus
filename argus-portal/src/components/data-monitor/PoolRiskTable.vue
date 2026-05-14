<template>
  <div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column label="应用 / 实例" min-width="210" fixed>
        <template #default="{ row }">
          <div class="cell-main">{{ row.appName || '-' }}</div>
          <div class="cell-sub">{{ row.displayPoolName }}</div>
        </template>
      </el-table-column>
      <el-table-column label="连接池类型" width="140">
        <template #default="{ row }">
          <el-tag effect="light">{{ row.poolType || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="活跃 / 最大" min-width="150">
        <template #default="{ row }">
          <div class="cell-main">{{ row.displayActiveConnections }} / {{ row.displayMaxConnections }}</div>
          <div class="cell-sub">使用率 {{ row.displayUsagePercent }}</div>
        </template>
      </el-table-column>
      <el-table-column label="等待 / 超时" min-width="150">
        <template #default="{ row }">
          <div class="cell-main">{{ row.displayWaitingThreads }} waiting</div>
          <div class="cell-sub">{{ row.displayTimeoutCount }} timeout</div>
        </template>
      </el-table-column>
      <el-table-column label="风险" min-width="280">
        <template #default="{ row }">
          <div class="cell-main">{{ row.displayRiskLevel }}</div>
          <div class="cell-sub">{{ row.displayRiskReason }}</div>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无连接池风险" />
  </div>
</template>

<script setup>
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
</style>

