<script setup lang="ts">
import { computed } from 'vue'
import type { RuleProfileItem } from '@/api/rule/types'
import {
  ElAlert,
  ElCheckbox,
  ElCheckboxGroup,
  ElInputNumber,
  ElTable,
  ElTableColumn,
  ElTag
} from 'element-plus'
import { scoringCategoryOptions, scoringDimensionDefinitions } from '../scoringMeta'

const props = defineProps<{
  profile: RuleProfileItem
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:categories', value: string[]): void
  (e: 'update:dimensionWeight', payload: { key: string; value: number }): void
}>()

const totalWeight = computed(() => {
  const dimensions = props.profile.scoringProfile.dimensions
  return (
    Number(dimensions.compliance || 0) +
    Number(dimensions.correctness || 0) +
    Number(dimensions.dataIntegrity || 0) +
    Number(dimensions.performance || 0) +
    Number(dimensions.maintainability || 0)
  )
})

const dimensionRows = computed(() =>
  scoringDimensionDefinitions.map((item) => ({
    ...item,
    weight: props.profile.scoringProfile.dimensions[item.key]
  }))
)

const categoriesModel = computed({
  get: () => props.profile.ruleProfile.standardCategories || [],
  set: (value: string[]) => {
    emit('update:categories', [...value])
  }
})

const updateWeight = (key: string, value: number | undefined) => {
  emit('update:dimensionWeight', { key, value: Number(value ?? 0) })
}
</script>

<template>
  <div class="flex flex-col gap-16px">
    <div>
      <div class="mb-10px text-14px font-600 text-[var(--el-text-color-primary)]">
        适用规范范围
      </div>
      <ElCheckboxGroup v-model="categoriesModel">
        <ElCheckbox
          v-for="item in scoringCategoryOptions"
          :key="item.value"
          :label="item.value"
          :disabled="readonly"
        >
          <div class="flex flex-col">
            <span>{{ item.label }}</span>
            <span class="text-12px text-[var(--el-text-color-secondary)]">
              {{ item.description }}
            </span>
          </div>
        </ElCheckbox>
      </ElCheckboxGroup>
    </div>

    <div class="flex items-center justify-between">
      <div class="text-14px font-600 text-[var(--el-text-color-primary)]">评分维度设计</div>
      <ElTag :type="totalWeight === 100 ? 'success' : 'danger'"
        >权重合计 {{ totalWeight }} / 100</ElTag
      >
    </div>

    <ElAlert
      v-if="totalWeight !== 100"
      type="error"
      show-icon
      :closable="false"
      title="评分维度权重合计必须等于 100，否则当前策略会让评分结果失真。"
    />

    <ElTable :data="dimensionRows" border stripe>
      <ElTableColumn label="维度" min-width="120">
        <template #default="{ row }">
          <div class="font-500">{{ row.name }}</div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="权重" width="140" align="center">
        <template #default="{ row }">
          <ElInputNumber
            :model-value="row.weight ?? undefined"
            class="w-100%"
            :min="0"
            :max="100"
            :disabled="readonly"
            @update:model-value="updateWeight(row.key, Number($event ?? 0))"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="含义" min-width="220">
        <template #default="{ row }">
          <div class="text-13px leading-20px">{{ row.description }}</div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="评审关注点" min-width="260">
        <template #default="{ row }">
          <div class="text-13px leading-20px text-[var(--el-text-color-secondary)]">
            {{ row.focus }}
          </div>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
