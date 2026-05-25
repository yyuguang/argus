<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cloneDeep, isEqual } from 'lodash-es'
import { ContentWrap } from '@/components/ContentWrap'
import { BaseButton } from '@/components/Button'
import { hasPermi } from '@/components/Permission/src/utils'
import { listScmConfigsApi } from '@/api/scm'
import type { ScmConfigItem } from '@/api/scm/types'
import {
  buildEmptyRuleProfile,
  getRuleProfileApi,
  normalizeRuleProfile,
  saveRuleProfileApi
} from '@/api/rule'
import type { RuleProfileItem } from '@/api/rule/types'
import { Check, Refresh, SetUp } from '@element-plus/icons-vue'
import { ElAlert, ElCard, ElDivider, ElMessage } from 'element-plus'
import RuleScmContextBar from '../components/RuleScmContextBar.vue'
import BlockingRulePanel from './components/BlockingRulePanel.vue'
import ScoringDimensionTable from './components/ScoringDimensionTable.vue'
import ScoringPreviewPanel from './components/ScoringPreviewPanel.vue'
import ScoringScopePanel from './components/ScoringScopePanel.vue'
import SeverityStrategyMatrix from './components/SeverityStrategyMatrix.vue'

const route = useRoute()
const router = useRouter()

const scmOptions = ref<ScmConfigItem[]>([])
const scmLoading = ref(false)
const selectedScmConfigId = ref(String(route.query.scmConfigId || ''))
const loading = ref(false)
const saveLoading = ref(false)
const profile = ref<RuleProfileItem>(buildEmptyRuleProfile())
const loadedProfile = ref<RuleProfileItem>(buildEmptyRuleProfile())

const canUpdateScoring = computed(() => hasPermi('scoring-update'))
const hasUnsavedChanges = computed(() => !isEqual(profile.value, loadedProfile.value))
const selectedScmOption = computed(
  () => scmOptions.value.find((item) => String(item.id) === selectedScmConfigId.value) || null
)

const parsedRawReviewConfig = computed<Record<string, any> | null>(() => {
  const rawText = selectedScmOption.value?.reviewConfig
  if (!rawText) {
    return null
  }
  try {
    return JSON.parse(rawText)
  } catch {
    return null
  }
})

const hasScoringOverride = computed(() => {
  return Boolean(parsedRawReviewConfig.value?.scoring)
})

const hasCategoryOverride = computed(() => {
  return Boolean(parsedRawReviewConfig.value?.rule?.standardCategories)
})

const syncRouteQuery = () => {
  router.replace({
    path: '/rule-governance/scoring-policies',
    query: {
      ...(selectedScmConfigId.value ? { scmConfigId: selectedScmConfigId.value } : {})
    }
  })
}

const loadScmOptions = async () => {
  scmLoading.value = true
  try {
    const res = await listScmConfigsApi()
    scmOptions.value = (res.data || []).filter((item) => item.enabled !== false)
  } finally {
    scmLoading.value = false
  }
}

const loadProfile = async () => {
  if (!selectedScmConfigId.value) return
  loading.value = true
  try {
    const res = await getRuleProfileApi(selectedScmConfigId.value)
    const normalized = normalizeRuleProfile(res.data)
    profile.value = cloneDeep(normalized)
    loadedProfile.value = cloneDeep(normalized)
  } finally {
    loading.value = false
  }
}

const handleScmChange = (value: string) => {
  selectedScmConfigId.value = value
  syncRouteQuery()
}

const saveProfile = async () => {
  if (!selectedScmConfigId.value) return
  saveLoading.value = true
  try {
    const res = await saveRuleProfileApi(selectedScmConfigId.value, profile.value)
    const normalized = normalizeRuleProfile(res.data)
    profile.value = cloneDeep(normalized)
    loadedProfile.value = cloneDeep(normalized)
    await loadScmOptions()
    ElMessage.success(res.message || '评分策略保存成功')
  } finally {
    saveLoading.value = false
  }
}

const revertChanges = () => {
  profile.value = cloneDeep(loadedProfile.value)
}

const resetToDefaults = () => {
  const defaultProfile = buildEmptyRuleProfile()
  profile.value = {
    ...cloneDeep(profile.value),
    scoringProfile: cloneDeep(defaultProfile.scoringProfile),
    ruleProfile: cloneDeep(defaultProfile.ruleProfile)
  }
}

const updateCategories = (value: string[]) => {
  profile.value.ruleProfile.standardCategories = [...value]
}

const updateDimensionWeight = (payload: { key: string; value: number }) => {
  profile.value.scoringProfile.dimensions[payload.key] = payload.value
}

const updateBlockThreshold = (value: number) => {
  profile.value.scoringProfile.blockThreshold = value
}

const updateCriticalDirectBlock = (value: boolean) => {
  profile.value.scoringProfile.blockingRules.criticalDirectBlock = value
}

const updateMajorBlockThreshold = (value: number | null) => {
  profile.value.scoringProfile.blockingRules.majorBlockThreshold = value
}

const updateSuggestionOnlyBlockEnabled = (value: boolean) => {
  profile.value.scoringProfile.blockingRules.suggestionOnlyBlockEnabled = value
}

const updateSeverityDefinition = (payload: {
  key: string
  label?: string
  deduction?: number
  examples?: string[]
}) => {
  const target = profile.value.scoringProfile.severityDefinitions[payload.key]
  if (!target) return
  if (payload.label !== undefined) {
    target.label = payload.label
  }
  if (payload.deduction !== undefined) {
    target.deduction = payload.deduction
  }
  if (payload.examples !== undefined) {
    target.examples = [...payload.examples]
  }
}

watch(
  () => selectedScmConfigId.value,
  () => {
    profile.value = buildEmptyRuleProfile()
    loadedProfile.value = buildEmptyRuleProfile()
    loadProfile()
  }
)

onMounted(async () => {
  await loadScmOptions()
  syncRouteQuery()
  await loadProfile()
})
</script>

<template>
  <ContentWrap>
    <ElAlert
      class="mb-12px"
      type="info"
      show-icon
      :closable="false"
      title="评分策略页现在按“策略范围、维度设计、严重度矩阵、阻断规则、效果预览”五段式组织。已接入能力做深，未接入规则会显式标注为待扩展。"
    />

    <RuleScmContextBar
      :model-value="selectedScmConfigId"
      :scm-options="scmOptions"
      :loading="scmLoading"
      hint="评分策略按仓库级维护，请先选择仓库，再查看当前策略是否覆写系统默认值。"
      @update:model-value="handleScmChange"
    />

    <ElAlert
      v-if="!selectedScmConfigId"
      type="warning"
      show-icon
      :closable="false"
      title="请先选择仓库，再查看该仓库当前命中的评分策略。"
    />

    <template v-else>
      <div class="mb-12px flex flex-wrap justify-end gap-8px">
        <BaseButton :icon="Refresh" :loading="loading" @click="loadProfile">刷新</BaseButton>
        <BaseButton
          :disabled="!canUpdateScoring || !hasUnsavedChanges"
          :loading="saveLoading"
          @click="revertChanges"
        >
          撤销未保存修改
        </BaseButton>
        <BaseButton
          :icon="SetUp"
          :disabled="!canUpdateScoring"
          :loading="saveLoading"
          @click="resetToDefaults"
        >
          恢复默认评分
        </BaseButton>
        <BaseButton
          type="primary"
          :icon="Check"
          :disabled="!canUpdateScoring || !hasUnsavedChanges"
          :loading="saveLoading"
          @click="saveProfile"
        >
          保存评分策略
        </BaseButton>
      </div>

      <div class="flex flex-col gap-12px">
        <ElCard shadow="never">
          <template #header>
            <div class="font-600">策略范围与生效来源</div>
          </template>
          <ScoringScopePanel
            :scm-project-name="selectedScmOption?.projectName"
            :has-scoring-override="hasScoringOverride"
            :has-category-override="hasCategoryOverride"
          />
        </ElCard>

        <ElCard shadow="never">
          <template #header>
            <div class="font-600">评分维度设计</div>
          </template>
          <ScoringDimensionTable
            :profile="profile"
            :readonly="!canUpdateScoring"
            @update:categories="updateCategories"
            @update:dimension-weight="updateDimensionWeight"
          />
        </ElCard>

        <div class="grid grid-cols-1 gap-12px xl:grid-cols-2">
          <ElCard shadow="never">
            <template #header>
              <div class="font-600">严重度策略矩阵</div>
            </template>
            <SeverityStrategyMatrix
              :profile="profile"
              :readonly="!canUpdateScoring"
              @update:severity-definition="updateSeverityDefinition"
            />
          </ElCard>

          <ElCard shadow="never">
            <template #header>
              <div class="font-600">阻断规则</div>
            </template>
            <BlockingRulePanel
              :block-threshold="profile.scoringProfile.blockThreshold"
              :critical-direct-block="profile.scoringProfile.blockingRules.criticalDirectBlock"
              :major-block-threshold="profile.scoringProfile.blockingRules.majorBlockThreshold"
              :suggestion-only-block-enabled="
                profile.scoringProfile.blockingRules.suggestionOnlyBlockEnabled
              "
              :readonly="!canUpdateScoring"
              @update:block-threshold="updateBlockThreshold"
              @update:critical-direct-block="updateCriticalDirectBlock"
              @update:major-block-threshold="updateMajorBlockThreshold"
              @update:suggestion-only-block-enabled="updateSuggestionOnlyBlockEnabled"
            />
          </ElCard>
        </div>

        <ElCard shadow="never">
          <template #header>
            <div class="font-600">效果预览</div>
          </template>
          <ScoringPreviewPanel :profile="profile" />
          <ElDivider />
          <ElAlert
            type="info"
            show-icon
            :closable="false"
            :title="
              canUpdateScoring
                ? '当前账号具备评分策略维护权限，保存后会写回当前仓库的 reviewConfig。'
                : '当前账号仅可查看评分策略效果，不具备修改权限。'
            "
          />
        </ElCard>
      </div>
    </template>
  </ContentWrap>
</template>
