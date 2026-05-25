<script setup lang="ts">
import type { ScmConfigItem } from '@/api/scm/types'
import { computed } from 'vue'
import { ElOption, ElSelect } from 'element-plus'

const props = defineProps<{
  modelValue?: string
  scmOptions: ScmConfigItem[]
  loading?: boolean
  title?: string
  hint?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const currentLabel = computed(() => {
  const current = props.scmOptions.find(
    (item) => String(item.id) === String(props.modelValue || '')
  )
  if (!current) return '未选择仓库'
  if (current.repoOwner && current.repoName) {
    return `${current.repoOwner}/${current.repoName}`
  }
  return current.projectName || String(current.id)
})
</script>

<template>
  <div class="mb-14px flex flex-wrap items-center gap-12px">
    <span class="text-14px color-[var(--el-text-color-regular)]">
      {{ title || '当前仓库上下文' }}
    </span>
    <ElSelect
      :model-value="modelValue"
      class="!w-360px max-w-100%"
      clearable
      filterable
      placeholder="选择仓库"
      :loading="loading"
      @update:model-value="emit('update:modelValue', String($event || ''))"
    >
      <ElOption
        v-for="item in scmOptions"
        :key="item.id"
        :label="
          item.repoOwner && item.repoName
            ? `${item.repoOwner}/${item.repoName}`
            : item.projectName || String(item.id)
        "
        :value="String(item.id)"
      />
    </ElSelect>
    <span class="text-12px color-[var(--el-text-color-secondary)]">
      {{ hint || currentLabel }}
    </span>
  </div>
</template>
