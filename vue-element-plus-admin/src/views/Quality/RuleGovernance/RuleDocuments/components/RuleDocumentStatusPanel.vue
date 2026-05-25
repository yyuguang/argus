<script setup lang="ts">
defineProps<{
  status?: string
  parseStatus?: string
  vectorStatus?: string
  chunkCount?: number
  latestErrorMessage?: string | null
}>()

const resolveTagType = (value?: string) => {
  if (value === 'ACTIVE' || value === 'SUCCESS') return 'success'
  if (value === 'FAILED' || value === 'DISABLED') return 'danger'
  if (value === 'PENDING' || value === 'DRAFT') return 'warning'
  return 'info'
}
</script>

<template>
  <div class="grid grid-cols-1 gap-12px md:grid-cols-3">
    <div class="rounded-6px border border-solid border-[var(--el-border-color-light)] p-12px">
      <div class="mb-8px text-12px color-[var(--el-text-color-secondary)]">文档可用性</div>
      <ElTag effect="light" :type="resolveTagType(status)">{{ status || '-' }}</ElTag>
      <div class="mt-8px text-12px leading-20px color-[var(--el-text-color-secondary)]">
        控制该规范是否会参与规则检索和评审注入。
      </div>
    </div>
    <div class="rounded-6px border border-solid border-[var(--el-border-color-light)] p-12px">
      <div class="mb-8px text-12px color-[var(--el-text-color-secondary)]">分块与解析</div>
      <ElTag effect="light" :type="resolveTagType(parseStatus)">{{ parseStatus || '-' }}</ElTag>
      <div class="mt-8px text-12px leading-20px color-[var(--el-text-color-secondary)]">
        当前共 {{ chunkCount ?? 0 }} 个分块，可用于后续向量写入和片段检索。
      </div>
    </div>
    <div class="rounded-6px border border-solid border-[var(--el-border-color-light)] p-12px">
      <div class="mb-8px text-12px color-[var(--el-text-color-secondary)]">向量索引</div>
      <ElTag effect="light" :type="resolveTagType(vectorStatus)">{{ vectorStatus || '-' }}</ElTag>
      <div class="mt-8px text-12px leading-20px color-[var(--el-text-color-secondary)]">
        {{ latestErrorMessage || '当前未记录最近错误，可直接参与规则召回。' }}
      </div>
    </div>
  </div>
</template>
