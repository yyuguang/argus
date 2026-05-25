<script setup lang="ts">
import { computed } from 'vue'
import type { RuleProfileItem } from '@/api/rule/types'
import { ElInput, ElInputNumber, ElTable, ElTableColumn, ElTag } from 'element-plus'

const props = defineProps<{
  profile: RuleProfileItem
  readonly?: boolean
}>()

const emit = defineEmits<{
  (
    e: 'update:severityDefinition',
    payload: { key: string; label?: string; deduction?: number; examples?: string[] }
  ): void
}>()

const severityRows = computed(() => {
  const preferredOrder = ['CRITICAL', 'MAJOR', 'MINOR', 'SUGGESTION']
  const keys = Object.keys(props.profile.scoringProfile.severityDefinitions || {})
  const orderedKeys = [
    ...preferredOrder.filter((key) => keys.includes(key)),
    ...keys.filter((key) => !preferredOrder.includes(key))
  ]
  return orderedKeys.map((key) => ({
    key,
    definition: props.profile.scoringProfile.severityDefinitions[key]
  }))
})

const resolveTagType = (severity: string) => {
  if (severity === 'CRITICAL') return 'danger'
  if (severity === 'MAJOR') return 'warning'
  if (severity === 'MINOR') return 'success'
  return 'info'
}

const updateSeverityLabel = (key: string, value: string) => {
  emit('update:severityDefinition', { key, label: value })
}

const updateSeverityDeduction = (key: string, value: number | undefined) => {
  emit('update:severityDefinition', { key, deduction: Number(value ?? 0) })
}

const updateSeverityExamples = (key: string, value: string) => {
  emit('update:severityDefinition', {
    key,
    examples: String(value || '')
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
  })
}
</script>

<template>
  <ElTable :data="severityRows" border stripe>
    <ElTableColumn label="严重度" width="110">
      <template #default="{ row }">
        <ElTag :type="resolveTagType(row.key)">{{ row.key }}</ElTag>
      </template>
    </ElTableColumn>
    <ElTableColumn label="展示标签" min-width="130">
      <template #default="{ row }">
        <ElInput
          :model-value="row.definition.label"
          maxlength="30"
          :readonly="readonly"
          @update:model-value="updateSeverityLabel(row.key, String($event || ''))"
        />
      </template>
    </ElTableColumn>
    <ElTableColumn label="扣分" width="120" align="center">
      <template #default="{ row }">
        <ElInputNumber
          :model-value="row.definition.deduction ?? undefined"
          class="w-100%"
          :min="0"
          :max="100"
          :disabled="readonly"
          @update:model-value="updateSeverityDeduction(row.key, Number($event ?? 0))"
        />
      </template>
    </ElTableColumn>
    <ElTableColumn label="示例问题" min-width="320">
      <template #default="{ row }">
        <ElInput
          :model-value="(row.definition.examples || []).join('\n')"
          type="textarea"
          :rows="3"
          :readonly="readonly"
          @update:model-value="updateSeverityExamples(row.key, String($event || ''))"
        />
      </template>
    </ElTableColumn>
  </ElTable>
</template>
