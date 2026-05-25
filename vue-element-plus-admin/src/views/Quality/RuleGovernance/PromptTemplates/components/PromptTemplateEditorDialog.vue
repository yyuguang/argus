<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Dialog } from '@/components/Dialog'
import { BaseButton } from '@/components/Button'
import PromptTemplateEffectivePanel from './PromptTemplateEffectivePanel.vue'
import PromptTemplatePreviewPanel from './PromptTemplatePreviewPanel.vue'
import PromptTextEditor from './PromptTextEditor.vue'
import { ElAlert } from 'element-plus'
import type { PromptTemplateSchemeItem } from '@/api/rule/types'

interface PromptTemplateEditorItem {
  templateCode: string
  templateName: string
  category: string
  categoryName: string
  templateScene: string
  templateSceneLabel: string
  description?: string
  supportScmOverride: boolean
  currentScope: 'GLOBAL' | 'SCM'
  hasScmOverride: boolean
}

const props = defineProps<{
  modelValue: boolean
  item?: PromptTemplateEditorItem
  currentScheme?: PromptTemplateSchemeItem | null
  effectiveScheme?: PromptTemplateSchemeItem | null
  readonly?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', payload: { contentText: string; remark: string }): void
}>()

const localContent = ref('')
const localRemark = ref('')

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const modeLabel = computed(() => {
  if (!props.item) return '模板详情'
  return props.item.currentScope === 'GLOBAL' ? '全局兜底方案' : '仓库级覆盖方案'
})

const dialogTitle = computed(() => {
  if (!props.item) return '提示词模板详情'
  return `${props.item.templateName} - ${modeLabel.value}`
})

const resetDraft = () => {
  localContent.value = props.currentScheme?.contentText || ''
  localRemark.value = props.currentScheme?.remark || ''
}

const save = () => {
  emit('save', {
    contentText: localContent.value,
    remark: localRemark.value
  })
}

watch(
  () => [
    props.modelValue,
    props.item?.templateCode,
    props.currentScheme?.contentText,
    props.currentScheme?.remark
  ],
  () => {
    resetDraft()
  },
  { immediate: true }
)
</script>

<template>
  <Dialog v-model="visible" :title="dialogTitle" width="1200px">
    <div v-if="item" class="flex flex-col gap-12px">
      <ElAlert
        type="info"
        show-icon
        :closable="false"
        :title="item.description || '维护当前模板组的正文内容，运行时会自动注入动态上下文。'"
      />

      <div class="rounded-6px border border-solid border-[var(--el-border-color-light)] p-12px">
        <div class="grid grid-cols-1 gap-12px md:grid-cols-4">
          <div>
            <div class="text-12px color-[var(--el-text-color-secondary)]">一级分类</div>
            <div class="mt-4px font-600">{{ item.categoryName }}</div>
          </div>
          <div>
            <div class="text-12px color-[var(--el-text-color-secondary)]">模板组编码</div>
            <div class="mt-4px font-600">{{ item.templateCode }}</div>
          </div>
          <div>
            <div class="text-12px color-[var(--el-text-color-secondary)]">模板场景</div>
            <div class="mt-4px font-600">{{ item.templateSceneLabel }}</div>
          </div>
          <div>
            <div class="text-12px color-[var(--el-text-color-secondary)]">当前编辑对象</div>
            <div class="mt-4px font-600">{{ modeLabel }}</div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-12px xl:grid-cols-[minmax(0,1fr)_320px]">
        <div class="rounded-6px border border-solid border-[var(--el-border-color-light)] p-12px">
          <div class="mb-12px flex flex-wrap items-center justify-between gap-8px">
            <div class="text-12px color-[var(--el-text-color-secondary)]">
              <template v-if="item.currentScope === 'GLOBAL'">
                当前正在维护系统兜底模板；未选择仓库或仓库未覆盖时，将直接使用这里的内容。
              </template>
              <template v-else-if="item.hasScmOverride">
                当前仓库已存在覆盖方案，保存后会直接更新该仓库的运行时 Prompt。
              </template>
              <template v-else>
                当前仓库还没有覆盖方案，保存后会新增一份仓库级 Prompt 模板。
              </template>
            </div>
            <BaseButton :disabled="readonly" @click="resetDraft">撤销本次修改</BaseButton>
          </div>

          <PromptTextEditor
            title="模板正文"
            description="这里维护真正交给 AI 的提示词正文，动态上下文会在运行时按占位符自动注入。"
            :model-value="localContent"
            :rows="22"
            :readonly="readonly"
            placeholder="请输入提示词模板正文"
            @update:model-value="localContent = $event"
          />

          <div class="mt-16px">
            <PromptTextEditor
              title="备注说明"
              description="可选，用于记录当前模板的适用范围、调整原因或特殊约束。"
              :model-value="localRemark"
              :rows="4"
              :readonly="readonly"
              placeholder="请输入备注说明"
              @update:model-value="localRemark = $event"
            />
          </div>
        </div>

        <div class="flex flex-col gap-12px">
          <PromptTemplateEffectivePanel
            :supported="item.supportScmOverride"
            :configured="item.hasScmOverride"
            :effective-source-label="
              effectiveScheme?.effectiveScope === 'SCM' ? '仓库级覆盖生效' : '系统全局兜底生效'
            "
            :scm-config-selected="item.currentScope === 'SCM'"
          />

          <PromptTemplatePreviewPanel
            :supported="true"
            :value="
              item.currentScope === 'SCM'
                ? effectiveScheme?.contentText || ''
                : currentScheme?.contentText || ''
            "
          />
        </div>
      </div>
    </div>
    <template #footer>
      <BaseButton @click="visible = false">关闭</BaseButton>
      <BaseButton type="primary" :loading="loading" :disabled="readonly" @click="save">
        保存当前模板
      </BaseButton>
    </template>
  </Dialog>
</template>
