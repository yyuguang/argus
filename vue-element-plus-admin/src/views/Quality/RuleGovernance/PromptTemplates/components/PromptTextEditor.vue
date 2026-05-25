<script setup lang="ts">
import { ElInput } from 'element-plus'

const props = defineProps<{
  title: string
  description?: string
  modelValue: string
  placeholder?: string
  rows?: number
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()
</script>

<template>
  <div>
    <div class="mb-8px">
      <div class="font-600">{{ props.title }}</div>
      <div v-if="props.description" class="mt-4px text-12px color-[var(--el-text-color-secondary)]">
        {{ props.description }}
      </div>
    </div>
    <ElInput
      :model-value="props.modelValue"
      type="textarea"
      :rows="props.rows || 16"
      :placeholder="props.placeholder || '请输入模板内容'"
      :readonly="props.readonly"
      @update:model-value="emit('update:modelValue', String($event || ''))"
    />
  </div>
</template>
