<script setup lang="ts">
import { computed } from 'vue'
import {
  ElAlert,
  ElDescriptions,
  ElDescriptionsItem,
  ElInputNumber,
  ElSwitch,
  ElTag
} from 'element-plus'

const props = defineProps<{
  blockThreshold: number | null
  criticalDirectBlock: boolean
  majorBlockThreshold: number | null
  suggestionOnlyBlockEnabled: boolean
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:blockThreshold', value: number): void
  (e: 'update:criticalDirectBlock', value: boolean): void
  (e: 'update:majorBlockThreshold', value: number | null): void
  (e: 'update:suggestionOnlyBlockEnabled', value: boolean): void
}>()

const threshold = computed({
  get: () => props.blockThreshold ?? 0,
  set: (value: number) => {
    emit('update:blockThreshold', Number(value ?? 0))
  }
})

const criticalDirectBlockModel = computed({
  get: () => props.criticalDirectBlock,
  set: (value: boolean) => {
    emit('update:criticalDirectBlock', value)
  }
})

const majorBlockThresholdModel = computed({
  get: () => props.majorBlockThreshold ?? undefined,
  set: (value: number | null | undefined) => {
    emit('update:majorBlockThreshold', value == null || value <= 0 ? null : Number(value))
  }
})

const suggestionOnlyBlockEnabledModel = computed({
  get: () => props.suggestionOnlyBlockEnabled,
  set: (value: boolean) => {
    emit('update:suggestionOnlyBlockEnabled', value)
  }
})

const passLineDescription = computed(() => {
  const value = threshold.value
  if (value <= 0) {
    return '当前阈值不限制得分下限，理论上所有结果都不会因总分触发阻塞。'
  }
  if (value >= 100) {
    return '当前阈值极高，除满分外几乎都会触发阻塞，请确认是否符合治理预期。'
  }
  return `当前要求最终得分不低于 ${value} 分，低于该分数将触发阻塞。`
})
</script>

<template>
  <div class="flex flex-col gap-12px">
    <div>
      <div class="mb-8px text-14px font-600 text-[var(--el-text-color-primary)]">已接入规则</div>
      <ElDescriptions :column="1" border>
        <ElDescriptionsItem label="总分阻断阈值">
          <div class="flex items-center gap-12px">
            <ElInputNumber
              v-model="threshold"
              class="w-160px"
              :min="0"
              :max="100"
              :disabled="readonly"
            />
            <ElTag type="warning">低于该分数即阻断</ElTag>
          </div>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="策略说明">
          {{ passLineDescription }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="CRITICAL 直接阻断">
          <div class="flex items-center gap-12px">
            <ElSwitch v-model="criticalDirectBlockModel" :disabled="readonly" />
            <ElTag :type="criticalDirectBlock ? 'danger' : 'info'">
              {{ criticalDirectBlock ? '启用' : '关闭' }}
            </ElTag>
          </div>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="MAJOR 数量阈值阻断">
          <div class="flex items-center gap-12px">
            <ElInputNumber
              v-model="majorBlockThresholdModel"
              class="w-160px"
              :min="0"
              :max="20"
              :disabled="readonly"
            />
            <ElTag type="warning">
              {{ majorBlockThreshold ? `达到 ${majorBlockThreshold} 个即阻断` : '未启用' }}
            </ElTag>
          </div>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="仅建议项是否允许阻断">
          <div class="flex items-center gap-12px">
            <ElSwitch v-model="suggestionOnlyBlockEnabledModel" :disabled="readonly" />
            <ElTag :type="suggestionOnlyBlockEnabled ? 'warning' : 'success'">
              {{ suggestionOnlyBlockEnabled ? '允许仅建议项阻断' : '仅建议项不阻断' }}
            </ElTag>
          </div>
        </ElDescriptionsItem>
      </ElDescriptions>
    </div>

    <ElAlert
      type="info"
      show-icon
      :closable="false"
      title="当前页面已接入三类阻断规则：总分阈值、CRITICAL 直接阻断、MAJOR 数量阈值阻断，以及“仅建议项是否允许阻断”的开关。"
    />
  </div>
</template>
