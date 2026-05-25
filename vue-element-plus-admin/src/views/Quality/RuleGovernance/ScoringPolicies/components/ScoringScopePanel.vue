<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElDescriptions, ElDescriptionsItem, ElTag } from 'element-plus'

const props = defineProps<{
  scmProjectName?: string
  hasScoringOverride: boolean
  hasCategoryOverride: boolean
}>()

const effectiveSourceLabel = computed(() => {
  if (props.hasScoringOverride || props.hasCategoryOverride) {
    return 'SCM 仓库级策略'
  }
  return '系统默认策略'
})

const fallbackLabel = computed(() => {
  if (props.hasScoringOverride || props.hasCategoryOverride) {
    return '删除仓库覆写后回退到系统默认策略'
  }
  return '当前未配置仓库覆写，直接使用系统默认策略'
})
</script>

<template>
  <div class="flex flex-col gap-12px">
    <ElDescriptions :column="2" border>
      <ElDescriptionsItem label="当前作用域">
        <ElTag type="success">SCM 仓库级</ElTag>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="当前仓库">
        {{ scmProjectName || '-' }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="当前生效来源">
        {{ effectiveSourceLabel }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="回退关系">
        {{ fallbackLabel }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="评分策略覆写">
        <ElTag :type="hasScoringOverride ? 'warning' : 'info'">
          {{ hasScoringOverride ? '已配置仓库覆写' : '使用系统默认' }}
        </ElTag>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="规范范围覆写">
        <ElTag :type="hasCategoryOverride ? 'warning' : 'info'">
          {{ hasCategoryOverride ? '已配置仓库覆写' : '使用系统默认' }}
        </ElTag>
      </ElDescriptionsItem>
    </ElDescriptions>

    <ElAlert
      type="info"
      show-icon
      :closable="false"
      title="当前页面优先维护仓库级评分策略。未配置仓库覆写时，评审会直接使用系统默认评分策略。"
    />
  </div>
</template>
