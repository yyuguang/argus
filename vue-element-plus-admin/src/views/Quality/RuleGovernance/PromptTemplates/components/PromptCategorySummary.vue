<script setup lang="ts">
interface SummaryItem {
  key: string
  title: string
  groupCount: number
  configuredCount: number
  fallbackCount: number
}

defineProps<{
  items: SummaryItem[]
  mode: 'GLOBAL' | 'SCM'
}>()
</script>

<template>
  <div class="mb-12px grid grid-cols-1 gap-12px md:grid-cols-2 xl:grid-cols-4">
    <div
      v-for="item in items"
      :key="item.key"
      class="rounded-6px border border-solid border-[var(--el-border-color-light)] bg-[var(--el-bg-color)] p-12px"
    >
      <div class="mb-8px font-600">{{ item.title }}</div>
      <div class="text-24px font-700 leading-1">{{ item.groupCount }}</div>
      <div class="mt-10px flex flex-col gap-4px text-12px color-[var(--el-text-color-secondary)]">
        <span>模板组 {{ item.groupCount }} 个</span>
        <span v-if="mode === 'GLOBAL'">已配置全局兜底 {{ item.configuredCount }} 组</span>
        <span v-else>当前仓库已覆盖 {{ item.configuredCount }} 组</span>
        <span v-if="mode === 'GLOBAL'">待补全全局兜底 {{ item.fallbackCount }} 组</span>
        <span v-else>仍走系统兜底 {{ item.fallbackCount }} 组</span>
      </div>
    </div>
  </div>
</template>
