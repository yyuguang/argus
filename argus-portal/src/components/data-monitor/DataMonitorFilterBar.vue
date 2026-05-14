<template>
  <section class="panel-card monitor-filter-panel">
    <el-form :inline="true" class="monitor-filter" @submit.prevent>
      <el-form-item label="应用">
        <el-input
          v-model.trim="filters.appName"
          clearable
          placeholder="appName"
          style="width: 180px"
          @keyup.enter="$emit('search')"
        />
      </el-form-item>
      <el-form-item label="环境">
        <el-select v-model="filters.environment" clearable placeholder="全部环境" style="width: 140px">
          <el-option label="prod" value="prod" />
          <el-option label="test" value="test" />
          <el-option label="staging" value="staging" />
          <el-option label="dev" value="dev" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间窗口">
        <el-select v-model="filters.window" style="width: 140px">
          <el-option label="最近 1 小时" value="1h" />
          <el-option label="最近 6 小时" value="6h" />
          <el-option label="最近 24 小时" value="24h" />
          <el-option label="最近 7 天" value="7d" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="$emit('search')">查询</el-button>
        <el-button @click="$emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </section>
</template>

<script setup>
defineProps({
  filters: {
    type: Object,
    required: true,
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['search', 'reset'])
</script>

<style scoped>
.monitor-filter-panel {
  padding: 18px;
}

.monitor-filter :deep(.el-form-item) {
  margin-bottom: 0;
}
</style>

