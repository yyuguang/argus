<script setup lang="ts">
import { computed } from 'vue'
import type { RuleProfileItem } from '@/api/rule/types'
import { ElDescriptions, ElDescriptionsItem, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { scoringPreviewScenarios } from '../scoringMeta'

const props = defineProps<{
  profile: RuleProfileItem
}>()

const previewRows = computed(() => {
  const threshold = Number(props.profile.scoringProfile.blockThreshold || 0)
  const severityDefinitions = props.profile.scoringProfile.severityDefinitions || {}
  const blockingRules = props.profile.scoringProfile.blockingRules
  return scoringPreviewScenarios.map((scenario) => {
    const totalDeduction = scenario.severities.reduce((sum, severity) => {
      return sum + Number(severityDefinitions[severity]?.deduction || 0)
    }, 0)
    const finalScore = Math.max(0, 100 - totalDeduction)
    const criticalCount = scenario.severities.filter((item) => item === 'CRITICAL').length
    const majorCount = scenario.severities.filter((item) => item === 'MAJOR').length
    const hasOnlySuggestion =
      scenario.severities.length > 0 && scenario.severities.every((item) => item === 'SUGGESTION')
    const blockedByThreshold =
      finalScore < threshold && (blockingRules.suggestionOnlyBlockEnabled || !hasOnlySuggestion)
    const blockedByCritical = blockingRules.criticalDirectBlock && criticalCount > 0
    const blockedByMajor =
      !!blockingRules.majorBlockThreshold && majorCount >= Number(blockingRules.majorBlockThreshold)
    return {
      ...scenario,
      totalDeduction,
      finalScore,
      blocked: blockedByThreshold || blockedByCritical || blockedByMajor
    }
  })
})

const summaryText = computed(() => {
  const threshold = Number(props.profile.scoringProfile.blockThreshold || 0)
  return `当前策略按总分阈值 ${threshold} 分判断是否阻断。以下示例用于帮助业务和研发快速理解该阈值会产生怎样的评审结果。`
})
</script>

<template>
  <div class="flex flex-col gap-12px">
    <ElDescriptions :column="1" border>
      <ElDescriptionsItem label="预览说明">
        {{ summaryText }}
      </ElDescriptionsItem>
    </ElDescriptions>

    <ElTable :data="previewRows" border stripe>
      <ElTableColumn label="场景" min-width="150">
        <template #default="{ row }">
          <div class="font-500">{{ row.name }}</div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="问题组合" min-width="180">
        <template #default="{ row }">
          <div class="flex flex-wrap gap-6px">
            <ElTag
              v-for="item in row.severities"
              :key="item"
              :type="
                item === 'CRITICAL'
                  ? 'danger'
                  : item === 'MAJOR'
                    ? 'warning'
                    : item === 'MINOR'
                      ? 'success'
                      : 'info'
              "
            >
              {{ item }}
            </ElTag>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="扣分" width="100" align="center" prop="totalDeduction" />
      <ElTableColumn label="最终得分" width="110" align="center" prop="finalScore" />
      <ElTableColumn label="是否阻断" width="110" align="center">
        <template #default="{ row }">
          <ElTag :type="row.blocked ? 'danger' : 'success'">
            {{ row.blocked ? '阻断' : '放行' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="说明" min-width="240" prop="description" />
    </ElTable>
  </div>
</template>
