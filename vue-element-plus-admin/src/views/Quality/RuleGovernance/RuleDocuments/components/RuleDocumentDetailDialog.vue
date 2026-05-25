<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Dialog } from '@/components/Dialog'
import {
  getRuleDocumentDetailApi,
  getRuleDocumentPreviewApi,
  normalizeRuleDocumentDetail,
  normalizeRuleDocumentPreview
} from '@/api/rule'
import type { RuleDocumentDetailItem, RuleDocumentPreviewItem } from '@/api/rule/types'
import RuleDocumentScopeTag from './RuleDocumentScopeTag.vue'
import RuleDocumentStatusPanel from './RuleDocumentStatusPanel.vue'
import {
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElMessage,
  ElTabPane,
  ElTabs
} from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  documentId?: string | number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const loading = ref(false)
const activeTab = ref('basic')
const detail = ref<RuleDocumentDetailItem>(normalizeRuleDocumentDetail())
const preview = ref<RuleDocumentPreviewItem>(normalizeRuleDocumentPreview())

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const loadData = async () => {
  if (!props.documentId) return
  loading.value = true
  try {
    const [detailRes, previewRes] = await Promise.all([
      getRuleDocumentDetailApi(props.documentId),
      getRuleDocumentPreviewApi(props.documentId)
    ])
    detail.value = normalizeRuleDocumentDetail(detailRes.data)
    preview.value = normalizeRuleDocumentPreview(previewRes.data)
  } catch (error: any) {
    ElMessage.error(error?.message || '加载规则文档详情失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.documentId],
  ([visibleValue, documentId]) => {
    if (visibleValue && documentId) {
      activeTab.value = 'basic'
      loadData()
    }
  },
  { immediate: true }
)
</script>

<template>
  <Dialog v-model="visible" title="规范文档详情" width="1040px" max-height="calc(100vh - 160px)">
    <ElTabs v-model="activeTab" v-loading="loading">
      <ElTabPane label="基本信息" name="basic">
        <RuleDocumentStatusPanel
          class="mb-12px"
          :status="detail.status"
          :parse-status="detail.parseStatus"
          :vector-status="detail.vectorStatus"
          :chunk-count="detail.chunkCount"
          :latest-error-message="detail.latestErrorMessage"
        />

        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="文档名称">{{ detail.documentName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="文档编码">{{ detail.documentCode || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="分类">{{ detail.category || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="作用域">
            <RuleDocumentScopeTag :scope="detail.scope" :scm-project-name="detail.scmProjectName" />
          </ElDescriptionsItem>
          <ElDescriptionsItem label="来源">{{ detail.sourceType || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="文件名">{{ detail.fileName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="版本号">{{ detail.versionNo ?? 0 }}</ElDescriptionsItem>
          <ElDescriptionsItem label="更新时间">{{ detail.updateTime || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="摘要" :span="2">
            <div class="whitespace-pre-wrap leading-22px">{{ detail.summaryText || '-' }}</div>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="备注" :span="2">
            <div class="whitespace-pre-wrap leading-22px">{{ detail.remark || '-' }}</div>
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElTabPane>

      <ElTabPane label="解析预览" name="preview">
        <div
          v-if="preview.plainText"
          class="max-h-460px overflow-auto rounded-6px bg-[var(--el-fill-color-light)] p-12px text-13px leading-22px whitespace-pre-wrap"
        >
          {{ preview.plainText }}
        </div>
        <ElEmpty v-else description="暂无解析预览" />
      </ElTabPane>

      <ElTabPane label="分块预览" name="chunks">
        <div v-if="preview.chunks.length" class="flex flex-col gap-12px">
          <div
            v-for="chunk in preview.chunks"
            :key="chunk.id || chunk.chunkNo"
            class="rounded-6px border border-solid border-[var(--el-border-color-light)] p-12px"
          >
            <div class="mb-6px flex items-center justify-between gap-12px">
              <div class="font-600">{{ chunk.chunkNo }} · {{ chunk.title || '未命名分块' }}</div>
              <div class="text-12px color-[var(--el-text-color-secondary)]">
                Token {{ chunk.tokenEstimate ?? '-' }} · {{ chunk.status || '-' }}
              </div>
            </div>
            <div class="text-13px leading-22px whitespace-pre-wrap">
              {{ chunk.contentText || '-' }}
            </div>
          </div>
        </div>
        <ElEmpty v-else description="暂无分块信息" />
      </ElTabPane>
    </ElTabs>
  </Dialog>
</template>
