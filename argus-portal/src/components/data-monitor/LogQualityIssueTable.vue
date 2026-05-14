<template>
  <div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column label="应用 / 表" min-width="230" fixed>
        <template #default="{ row }">
          <div class="cell-main">{{ row.appName || '-' }}</div>
          <div class="cell-sub">{{ row.displayTableName }}</div>
        </template>
      </el-table-column>
      <el-table-column label="问题类型" min-width="180">
        <template #default="{ row }">
          <el-tag :type="row.displayIssueTagType" effect="light">{{ row.displayIssueType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质量指标" min-width="220">
        <template #default="{ row }">
          <div class="cell-main">空响应 {{ row.displayEmptyResponseRate }}</div>
          <div class="cell-sub">缺 requestId {{ row.displayMissingRequestIdRate }}</div>
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="320">
        <template #default="{ row }">
          <div class="cell-main">{{ row.displaySummary }}</div>
          <div class="cell-sub">{{ row.displaySuggestion }}</div>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="120">
        <template #default="{ row }">
          <el-button link type="warning" @click.stop="$emit('ignore', row)">忽略</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无接口日志质量问题" />
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

defineEmits(['ignore'])
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

