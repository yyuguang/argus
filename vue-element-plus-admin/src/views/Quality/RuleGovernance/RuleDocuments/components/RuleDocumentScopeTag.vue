<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  scope?: string
  scmProjectName?: string
}>()

const tagType = computed(() => (props.scope === 'SCM' ? 'warning' : 'info'))
const label = computed(() => (props.scope === 'SCM' ? '仓库级' : '全局'))
</script>

<template>
  <div class="inline-flex items-center gap-8px">
    <ElTag effect="light" :type="tagType">
      {{ label }}
    </ElTag>
    <span
      v-if="scope === 'SCM' && scmProjectName"
      class="text-12px color-[var(--el-text-color-secondary)]"
    >
      {{ scmProjectName }}
    </span>
  </div>
</template>
