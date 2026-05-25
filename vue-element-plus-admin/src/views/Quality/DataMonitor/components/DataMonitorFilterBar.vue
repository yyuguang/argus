<template>
  <section class="monitor-filter-panel">
    <el-form :inline="true" class="monitor-filter" @submit.prevent>
      <el-form-item label="应用">
        <el-input
          v-model.trim="localFilters.appName"
          clearable
          placeholder="appName"
          style="width: 180px"
          @change="syncFilters"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="环境">
        <el-select
          v-model="localFilters.environment"
          clearable
          placeholder="全部环境"
          style="width: 140px"
          @change="syncFilters"
        >
          <el-option label="prod" value="prod" />
          <el-option label="test" value="test" />
          <el-option label="staging" value="staging" />
          <el-option label="dev" value="dev" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间窗口">
        <el-select v-model="localFilters.window" style="width: 140px" @change="syncFilters">
          <el-option label="最近 1 小时" value="1h" />
          <el-option label="最近 6 小时" value="6h" />
          <el-option label="最近 24 小时" value="24h" />
          <el-option label="最近 7 天" value="7d" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleSearch"> 查询 </el-button>
        <el-button @click="handleReset"> 重置 </el-button>
      </el-form-item>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { ElButton, ElForm, ElFormItem, ElInput, ElOption, ElSelect } from 'element-plus'

const props = defineProps<{
  filters: Record<string, any>
  loading?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:filters', value: Record<string, any>): void
  (event: 'search'): void
  (event: 'reset'): void
}>()

const localFilters = reactive({
  appName: '',
  environment: '',
  window: '24h'
})

watch(
  () => props.filters,
  (value) => {
    Object.assign(localFilters, {
      appName: value?.appName || '',
      environment: value?.environment || '',
      window: value?.window || '24h'
    })
  },
  { immediate: true, deep: true }
)

const syncFilters = () => {
  emit('update:filters', { ...localFilters })
}

const handleSearch = () => {
  syncFilters()
  emit('search')
}

const handleReset = () => {
  syncFilters()
  emit('reset')
}
</script>

<style scoped>
.monitor-filter-panel {
  padding: 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.monitor-filter :deep(.el-form-item) {
  margin-bottom: 0;
}
</style>
