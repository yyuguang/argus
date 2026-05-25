<script setup lang="ts">
defineProps<{
  value: string
  supported: boolean
}>()
</script>

<template>
  <div class="rounded-6px border border-solid border-[var(--el-border-color-light)] p-12px">
    <div class="mb-8px font-600">当前内容预览</div>
    <div
      v-if="supported && value"
      class="max-h-300px overflow-auto rounded-6px bg-[var(--el-fill-color-light)] p-12px text-13px leading-22px whitespace-pre-wrap"
    >
      {{ value }}
    </div>
    <div
      v-else-if="supported"
      class="rounded-6px bg-[var(--el-fill-color-light)] p-12px text-12px leading-22px color-[var(--el-text-color-secondary)]"
    >
      当前仓库未配置该槽位，评审时会继续回退到系统兜底模板。
    </div>
    <div
      v-else
      class="rounded-6px bg-[var(--el-fill-color-light)] p-12px text-12px leading-22px color-[var(--el-text-color-secondary)]"
    >
      当前接口尚未返回该槽位的正文内容，页面先展示资产盘点状态，待后续 Prompt 资产化链路补齐。
    </div>
  </div>
</template>
