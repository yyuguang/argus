<script setup lang="tsx">
import { ContentWrap } from '@/components/ContentWrap'
import { Search } from '@/components/Search'
import { Table, TableColumn } from '@/components/Table'
import { BaseButton } from '@/components/Button'
import { FormSchema } from '@/components/Form'
import {
  getReviewerProfileDetailApi,
  getReviewerProfilesApi,
  getReviewerTopIssuesApi,
  getReviewerTrendApi
} from '@/api/profile'
import type { ReviewerProfileItem, ReviewerTopIssueItem } from '@/api/profile/types'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElProgress,
  ElSkeleton,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTimeline,
  ElTimelineItem
} from 'element-plus'
import { Refresh, View } from '@element-plus/icons-vue'

interface SearchParams {
  scmProvider?: string
  days?: number
  keyword?: string
  sortBy?: string
}

interface IssueItem {
  rule?: string
  tag?: string
  count: number
}

interface DimensionItem {
  key: string
  label: string
  score: string
  percent: number
}

type EnrichedProfile = ReviewerProfileItem & {
  _dimensionStats: Record<string, number>
  _topIssueRules: IssueItem[]
  _topIssueTags: IssueItem[]
  _scoreTrend: number[]
  _recentReviews: Record<string, any>[]
}

const providerOptions = [
  { label: 'GitLab', value: 'gitlab' },
  { label: 'GitHub', value: 'github' },
  { label: 'Gitee', value: 'gitee' }
]

const sortOptions = [
  { label: '平均分从高到低', value: 'scoreDesc' },
  { label: '平均分从低到高', value: 'scoreAsc' },
  { label: '评审次数从多到少', value: 'reviewsDesc' },
  { label: '最近评审从近到远', value: 'recentDesc' }
]

const dimensionLabels: Record<string, string> = {
  compliance: '规范合规',
  correctness: '逻辑正确',
  dataSafety: '数据安全',
  performance: '性能风险',
  maintainability: '可维护性'
}

const searchSchema = reactive<FormSchema[]>([
  {
    field: 'scmProvider',
    label: 'SCM 平台',
    component: 'Select',
    componentProps: {
      options: providerOptions
    },
    value: 'github'
  },
  {
    field: 'days',
    label: '统计窗口',
    component: 'Select',
    componentProps: {
      options: [
        { label: '近 7 天', value: 7 },
        { label: '近 30 天', value: 30 },
        { label: '近 90 天', value: 90 }
      ]
    },
    value: 30
  },
  {
    field: 'sortBy',
    label: '排序方式',
    component: 'Select',
    componentProps: {
      options: sortOptions
    },
    value: 'scoreDesc'
  },
  {
    field: 'keyword',
    label: '关键字',
    component: 'Input',
    componentProps: {
      placeholder: '提交者 / authorId',
      clearable: true
    }
  }
])

const loading = ref(false)
const topIssueLoading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const activeDetailTab = ref('basic')
const errorMessage = ref('')
const activeRule = ref('')
const profiles = ref<ReviewerProfileItem[]>([])
const topIssues = ref<ReviewerTopIssueItem[]>([])
const selectedProfile = ref<EnrichedProfile>()
const detailTrend = ref<number[]>([])
const searchParams = ref<SearchParams>({
  scmProvider: 'github',
  days: 30,
  keyword: '',
  sortBy: 'scoreDesc'
})

const parseJson = (value: unknown, fallback: any) => {
  if (!value) {
    return fallback
  }
  if (typeof value === 'object') {
    return value
  }
  try {
    return JSON.parse(String(value))
  } catch {
    return fallback
  }
}

const toEnrichedProfile = (profile: ReviewerProfileItem): EnrichedProfile => ({
  ...profile,
  _dimensionStats: parseJson(profile.dimensionStats, {}) as Record<string, number>,
  _topIssueRules: normalizeIssueItems(
    parseJson(profile.topIssueRules, []) as Record<string, any>[],
    'rule'
  ),
  _topIssueTags: normalizeIssueItems(
    parseJson(profile.topIssueTags, []) as Record<string, any>[],
    'tag'
  ),
  _scoreTrend: parseJson(profile.scoreTrend, []) as number[],
  _recentReviews: parseJson(profile.recentReviews, []) as Record<string, any>[]
})

const enrichedProfiles = computed(() => profiles.value.map(toEnrichedProfile))

const filteredProfiles = computed(() => {
  const keyword = String(searchParams.value.keyword || '')
    .trim()
    .toLowerCase()

  let rows = enrichedProfiles.value.filter((profile) => {
    const text = `${profile.authorName || ''} ${profile.authorId || ''}`.toLowerCase()
    const matchesKeyword = !keyword || text.includes(keyword)
    const matchesRule =
      !activeRule.value || topRules(profile).some((item) => item.rule === activeRule.value)
    return matchesKeyword && matchesRule
  })

  rows = [...rows].sort((a, b) => {
    if (searchParams.value.sortBy === 'scoreAsc') {
      return numericScore(a.avgScore) - numericScore(b.avgScore)
    }
    if (searchParams.value.sortBy === 'reviewsDesc') {
      return Number(b.totalReviews || 0) - Number(a.totalReviews || 0)
    }
    if (searchParams.value.sortBy === 'recentDesc') {
      return dateValue(b.lastReviewAt) - dateValue(a.lastReviewAt)
    }
    return numericScore(b.avgScore) - numericScore(a.avgScore)
  })
  return rows
})

const stats = computed(() => {
  const scoreRows = enrichedProfiles.value.filter(
    (item) => item.avgScore !== null && item.avgScore !== undefined && item.avgScore !== ''
  )
  const avgScore = scoreRows.length
    ? Math.round(
        scoreRows.reduce((sum, item) => sum + numericScore(item.avgScore), 0) / scoreRows.length
      )
    : 0
  return {
    memberCount: enrichedProfiles.value.length,
    avgScore,
    riskCount: enrichedProfiles.value.filter((item) => {
      const score = numericScore(item.avgScore)
      return score > 0 && score < 70
    }).length,
    activeCount: enrichedProfiles.value.filter((item) =>
      isWithinDays(item.lastReviewAt, Number(searchParams.value.days || 30))
    ).length
  }
})

const summaryCards = computed(() => [
  {
    key: 'memberCount',
    title: '已沉淀成员',
    value: stats.value.memberCount,
    hint: `${providerLabel(searchParams.value.scmProvider)} 画像`
  },
  {
    key: 'avgScore',
    title: '团队平均分',
    value: stats.value.avgScore || '-',
    hint: scoreLevel(stats.value.avgScore)
  },
  {
    key: 'riskCount',
    title: '低分风险',
    value: stats.value.riskCount,
    hint: '平均分低于 70'
  },
  {
    key: 'activeCount',
    title: '活跃成员',
    value: stats.value.activeCount,
    hint: `近 ${searchParams.value.days || 30} 天有评审`
  }
])

const providerLabel = (provider?: string) => {
  if (provider === 'gitlab') return 'GitLab'
  if (provider === 'github') return 'GitHub'
  if (provider === 'gitee') return 'Gitee'
  return provider || '-'
}

const providerTagType = (provider?: string) => {
  if (provider === 'gitlab') return 'warning'
  if (provider === 'github') return 'info'
  if (provider === 'gitee') return 'danger'
  return 'primary'
}

const numericScore = (value?: number | string) => {
  const score = Number(value)
  return Number.isFinite(score) ? score : 0
}

const formatScore = (value?: number | string) => {
  const score = numericScore(value)
  return score ? score.toFixed(0) : '-'
}

const scoreLevel = (value?: number | string) => {
  const score = numericScore(value)
  if (!score) return '暂无'
  if (score >= 90) return 'A'
  if (score >= 80) return 'B'
  if (score >= 70) return 'C'
  return 'Risk'
}

const scoreTagType = (value?: number | string) => {
  const score = numericScore(value)
  if (!score) return 'info'
  if (score >= 90) return 'success'
  if (score >= 80) return 'primary'
  if (score >= 70) return 'warning'
  return 'danger'
}

const scorePercent = (value?: number | string) => {
  return Math.max(0, Math.min(100, Math.round(numericScore(value))))
}

const formatDate = (value?: string) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const dateValue = (value?: string) => {
  if (!value) return 0
  const time = new Date(value).getTime()
  return Number.isFinite(time) ? time : 0
}

const isWithinDays = (value: string | undefined, days: number) => {
  const time = dateValue(value)
  if (!time) return false
  return Date.now() - time <= days * 24 * 60 * 60 * 1000
}

const avatarText = (name?: string) => {
  if (!name) return 'U'
  return String(name).trim().slice(0, 1).toUpperCase()
}

function normalizeIssueItems(items: Record<string, any>[], keyName: 'rule' | 'tag'): IssueItem[] {
  if (!Array.isArray(items)) return []
  return items
    .map((item) => ({
      [keyName]: item[keyName] || item.name || item.rule || item.tag || '未命名',
      count: Number(item.count || item.value || 0)
    }))
    .filter((item) => item[keyName])
}

const topRules = (profile: EnrichedProfile) => profile._topIssueRules || []

const topTags = (profile: EnrichedProfile) => profile._topIssueTags || []

const dimensionItems = (profile: EnrichedProfile): DimensionItem[] => {
  const data = profile._dimensionStats || {}
  return Object.entries(dimensionLabels).map(([key, label]) => {
    const value = Number(data[key])
    return {
      key,
      label,
      score: Number.isFinite(value) ? value.toFixed(0) : '-',
      percent: Number.isFinite(value) ? Math.max(0, Math.min(100, Math.round(value))) : 0
    }
  })
}

const weakestDimension = (profile: EnrichedProfile) => {
  const rows = dimensionItems(profile).filter((item) => item.score !== '-')
  if (!rows.length) {
    return { label: '暂无', score: '-' }
  }
  return [...rows].sort((a, b) => Number(a.score) - Number(b.score))[0]
}

const recentReviews = (profile: EnrichedProfile) => profile._recentReviews || []

const issuePercent = (count?: number) => {
  const max = Math.max(...topIssues.value.map((item) => Number(item.count || 0)), 1)
  return Math.round((Number(count || 0) / max) * 100)
}

const tableColumns = reactive<TableColumn[]>([
  {
    field: 'authorName',
    label: '提交者',
    minWidth: 220,
    slots: {
      default: ({ row }: { row: EnrichedProfile }) => (
        <div class="personal-person-cell">
          <span class="personal-avatar-mark">{avatarText(row.authorName)}</span>
          <div>
            <div class="font-600">{row.authorName || '-'}</div>
            <div class="text-12px color-[var(--el-text-color-secondary)]">
              {row.authorId || '-'}
            </div>
          </div>
        </div>
      )
    }
  },
  {
    field: 'scmProvider',
    label: '平台',
    width: 110,
    slots: {
      default: ({ row }: { row: EnrichedProfile }) => (
        <ElTag type={providerTagType(row.scmProvider)} effect="light">
          {providerLabel(row.scmProvider)}
        </ElTag>
      )
    }
  },
  {
    field: 'avgScore',
    label: '质量评分',
    minWidth: 180,
    slots: {
      default: ({ row }: { row: EnrichedProfile }) => (
        <div>
          <div class="personal-score-row">
            <span class="font-600">{formatScore(row.avgScore)}</span>
            <ElTag type={scoreTagType(row.avgScore)} effect="light">
              {scoreLevel(row.avgScore)}
            </ElTag>
          </div>
          <ElProgress percentage={scorePercent(row.avgScore)} strokeWidth={7} showText={false} />
        </div>
      )
    }
  },
  {
    field: 'totalReviews',
    label: '评审次数',
    width: 110
  },
  {
    field: 'weakestDimension',
    label: '维度短板',
    minWidth: 160,
    slots: {
      default: ({ row }: { row: EnrichedProfile }) => {
        const weakest = weakestDimension(row)
        return (
          <div>
            <div>{weakest.label}</div>
            <div class="text-12px color-[var(--el-text-color-secondary)]">{weakest.score}</div>
          </div>
        )
      }
    }
  },
  {
    field: 'topIssues',
    label: '高频问题',
    minWidth: 240,
    slots: {
      default: ({ row }: { row: EnrichedProfile }) =>
        topRules(row).length ? (
          <div class="personal-rule-tags">
            {topRules(row)
              .slice(0, 2)
              .map((item) => (
                <ElTag key={`${row.authorName}-${item.rule}`} effect="plain">
                  {item.rule} · {item.count}
                </ElTag>
              ))}
          </div>
        ) : (
          <span class="text-12px color-[var(--el-text-color-secondary)]">暂无</span>
        )
    }
  },
  {
    field: 'lastReviewAt',
    label: '最近评审',
    width: 180,
    slots: {
      default: ({ row }: { row: EnrichedProfile }) => <>{formatDate(row.lastReviewAt)}</>
    }
  },
  {
    field: 'action',
    label: '操作',
    width: 110,
    fixed: 'right',
    slots: {
      default: ({ row }: { row: EnrichedProfile }) => (
        <div class="flex flex-wrap gap-8px">
          <BaseButton type="primary" icon={View} onClick={() => openProfile(row)}>
            详情
          </BaseButton>
        </div>
      )
    }
  }
])

const setSearchParams = (params: Record<string, any>) => {
  searchParams.value = {
    scmProvider: params.scmProvider || 'github',
    days: Number(params.days || 30),
    keyword: params.keyword || '',
    sortBy: params.sortBy || 'scoreDesc'
  }
  activeRule.value = ''
  loadAll()
}

const toggleRule = (rule?: string) => {
  activeRule.value = activeRule.value === rule ? '' : String(rule || '')
}

const loadProfiles = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getReviewerProfilesApi({
      scmProvider: String(searchParams.value.scmProvider || 'github')
    })
    profiles.value = res.data || []
  } catch (error: any) {
    errorMessage.value = error?.message || '加载个人代码质量画像失败'
  } finally {
    loading.value = false
  }
}

const loadTopIssues = async () => {
  topIssueLoading.value = true
  try {
    const res = await getReviewerTopIssuesApi({
      scmProvider: String(searchParams.value.scmProvider || 'github'),
      days: Number(searchParams.value.days || 30)
    })
    topIssues.value = res.data || []
  } catch (error: any) {
    errorMessage.value = error?.message || '加载团队高频问题失败'
  } finally {
    topIssueLoading.value = false
  }
}

const loadAll = async () => {
  await Promise.all([loadProfiles(), loadTopIssues()])
}

const openProfile = async (row: EnrichedProfile) => {
  detailVisible.value = true
  detailLoading.value = true
  activeDetailTab.value = 'basic'
  selectedProfile.value = row
  detailTrend.value = row._scoreTrend || []
  try {
    const [profileRes, trendRes] = await Promise.all([
      getReviewerProfileDetailApi(row.authorName || '', {
        scmProvider: row.scmProvider || String(searchParams.value.scmProvider || 'github')
      }),
      getReviewerTrendApi(row.authorName || '', {
        scmProvider: row.scmProvider || String(searchParams.value.scmProvider || 'github')
      })
    ])
    const merged = {
      ...row,
      ...(profileRes.data || {})
    }
    selectedProfile.value = toEnrichedProfile(merged)
    detailTrend.value = Array.isArray(trendRes.data)
      ? trendRes.data
      : selectedProfile.value._scoreTrend || []
  } catch (error: any) {
    errorMessage.value = error?.message || '加载个人画像详情失败'
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  loadAll()
})
</script>

<template>
  <ContentWrap>
    <div class="mb-16px">
      <div class="text-12px color-[var(--el-text-color-secondary)] uppercase tracking-[0.08em]">
        Quality Intelligence
      </div>
      <div class="mt-6px flex items-center justify-between gap-12px">
        <div>
          <div class="text-22px font-600">个人代码质量</div>
          <div class="mt-6px text-14px color-[var(--el-text-color-secondary)]">
            基于 AI 评审结果沉淀提交者画像，跟踪团队质量趋势、个人短板和高频违规规则。
          </div>
        </div>
        <BaseButton :icon="Refresh" :loading="loading || topIssueLoading" @click="loadAll">
          刷新
        </BaseButton>
      </div>
    </div>

    <Search :schema="searchSchema" @reset="setSearchParams" @search="setSearchParams" />

    <div class="personal-summary-grid">
      <ElCard v-for="card in summaryCards" :key="card.key" shadow="never">
        <div class="personal-summary-card">
          <span>{{ card.title }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.hint }}</small>
        </div>
      </ElCard>
    </div>

    <div class="personal-workbench">
      <ElCard shadow="never" class="personal-main-card">
        <template #header>
          <div class="flex items-center justify-between gap-12px">
            <div>
              <div class="text-16px font-600">人员画像</div>
              <div class="mt-4px text-13px color-[var(--el-text-color-secondary)]">
                支持按提交者、authorId、评分和高频规则快速定位。
              </div>
            </div>
            <ElTag effect="light">{{ filteredProfiles.length }} 人</ElTag>
          </div>
        </template>

        <ElAlert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
          class="mb-12px"
        />

        <Table
          :columns="tableColumns"
          :data="filteredProfiles"
          :loading="loading"
          :pagination="undefined"
        />

        <ElEmpty v-if="!loading && !filteredProfiles.length" description="暂无个人代码质量画像" />
      </ElCard>

      <ElCard shadow="never" class="personal-side-card">
        <template #header>
          <div>
            <div class="text-16px font-600">团队高频问题</div>
            <div class="mt-4px text-13px color-[var(--el-text-color-secondary)]">
              近 {{ searchParams.days || 30 }} 天聚合 Top 规则
            </div>
          </div>
        </template>

        <ElSkeleton v-if="topIssueLoading" :rows="6" animated />
        <ElEmpty v-else-if="!topIssues.length" description="暂无高频问题" />
        <div v-else class="personal-issue-list">
          <button
            v-for="issue in topIssues"
            :key="issue.rule"
            type="button"
            :class="{ active: activeRule === issue.rule }"
            @click="toggleRule(issue.rule)"
          >
            <div class="flex items-center justify-between gap-10px">
              <span>{{ issue.rule || '-' }}</span>
              <strong>{{ issue.count || 0 }}</strong>
            </div>
            <ElProgress
              :percentage="issuePercent(issue.count)"
              :stroke-width="6"
              :show-text="false"
            />
          </button>
        </div>
      </ElCard>
    </div>

    <ElDrawer v-model="detailVisible" title="个人代码质量详情" size="860px" destroy-on-close>
      <ElSkeleton v-if="detailLoading" :rows="10" animated />

      <template v-else-if="selectedProfile">
        <div class="personal-detail-hero">
          <span class="personal-detail-avatar">{{ avatarText(selectedProfile.authorName) }}</span>
          <div class="personal-detail-main">
            <div class="text-13px color-[var(--el-text-color-secondary)]">
              {{ providerLabel(selectedProfile.scmProvider) }}
            </div>
            <div class="text-22px font-600">{{ selectedProfile.authorName || '-' }}</div>
            <div class="text-13px color-[var(--el-text-color-secondary)]">
              {{ selectedProfile.authorId || '-' }}
            </div>
          </div>
          <div class="text-right">
            <div class="text-12px color-[var(--el-text-color-secondary)]">平均分</div>
            <div class="text-28px font-600">{{ formatScore(selectedProfile.avgScore) }}</div>
            <ElTag :type="scoreTagType(selectedProfile.avgScore)" effect="light">
              {{ scoreLevel(selectedProfile.avgScore) }}
            </ElTag>
          </div>
        </div>

        <ElTabs v-model="activeDetailTab">
          <ElTabPane label="基础画像" name="basic">
            <ElDescriptions :column="2" border class="mb-16px">
              <ElDescriptionsItem label="累计评审">
                {{ selectedProfile.totalReviews || 0 }} 次
              </ElDescriptionsItem>
              <ElDescriptionsItem label="平均等级">
                {{ scoreLevel(selectedProfile.avgScore) }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="首次评审">
                {{ formatDate(selectedProfile.firstReviewAt) }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="最近评审">
                {{ formatDate(selectedProfile.lastReviewAt) }}
              </ElDescriptionsItem>
            </ElDescriptions>

            <div class="personal-dimension-list">
              <div v-for="item in dimensionItems(selectedProfile)" :key="item.key">
                <div class="flex items-center justify-between gap-12px mb-6px">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.score }}</strong>
                </div>
                <ElProgress :percentage="item.percent" :stroke-width="8" :show-text="false" />
              </div>
            </div>
          </ElTabPane>

          <ElTabPane label="趋势分析" name="trend">
            <ElEmpty v-if="!detailTrend.length" description="暂无趋势数据" />
            <div v-else class="personal-sparkline-bars">
              <div v-for="(score, index) in detailTrend" :key="index">
                <span :style="{ height: `${Math.max(12, score)}%` }"></span>
                <small>{{ score }}</small>
              </div>
            </div>
          </ElTabPane>

          <ElTabPane label="问题与记录" name="issues">
            <div class="personal-issue-grid">
              <ElCard shadow="never">
                <template #header>高频规则</template>
                <ElEmpty v-if="!topRules(selectedProfile).length" description="暂无高频规则" />
                <div v-else class="personal-mini-list">
                  <p v-for="item in topRules(selectedProfile)" :key="item.rule">
                    <strong>{{ item.rule }}</strong>
                    <em>{{ item.count }}</em>
                  </p>
                </div>
              </ElCard>

              <ElCard shadow="never">
                <template #header>高频标签</template>
                <ElEmpty v-if="!topTags(selectedProfile).length" description="暂无高频标签" />
                <div v-else class="personal-mini-list">
                  <p v-for="item in topTags(selectedProfile)" :key="item.tag">
                    <strong>{{ item.tag }}</strong>
                    <em>{{ item.count }}</em>
                  </p>
                </div>
              </ElCard>
            </div>

            <div class="mt-16px">
              <div class="mb-12px text-15px font-600">最近评审</div>
              <ElEmpty
                v-if="!recentReviews(selectedProfile).length"
                description="暂无最近评审摘要"
              />
              <ElTimeline v-else>
                <ElTimelineItem
                  v-for="item in recentReviews(selectedProfile)"
                  :key="item.taskId || `${item.projectName}-${item.reviewedAt || item.createTime}`"
                  :timestamp="formatDate(item.reviewedAt || item.createTime)"
                >
                  <div class="personal-review-card">
                    <strong>{{ item.projectName || item.repoName || '未知仓库' }}</strong>
                    <span>{{ item.score ?? '-' }} / {{ item.scoreLevel || '-' }}</span>
                  </div>
                </ElTimelineItem>
              </ElTimeline>
            </div>
          </ElTabPane>
        </ElTabs>
      </template>
    </ElDrawer>
  </ContentWrap>
</template>

<style scoped>
.personal-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.personal-summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.personal-summary-card span,
.personal-summary-card small {
  color: var(--el-text-color-secondary);
}

.personal-summary-card strong {
  font-size: 30px;
  line-height: 1.1;
}

.personal-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
}

.personal-main-card,
.personal-side-card {
  min-width: 0;
}

.personal-person-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.personal-avatar-mark,
.personal-detail-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.personal-avatar-mark {
  width: 36px;
  height: 36px;
}

.personal-detail-avatar {
  width: 56px;
  height: 56px;
  font-size: 22px;
}

.personal-score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.personal-rule-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.personal-issue-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.personal-issue-list button {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
  text-align: left;
  cursor: pointer;
}

.personal-issue-list button.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.personal-detail-hero {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.personal-detail-main {
  flex: 1;
  min-width: 0;
}

.personal-dimension-list {
  display: grid;
  gap: 14px;
}

.personal-sparkline-bars {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(48px, 1fr));
  gap: 12px;
  align-items: end;
  min-height: 240px;
  padding: 16px 0;
}

.personal-sparkline-bars > div {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: end;
  gap: 8px;
  height: 220px;
}

.personal-sparkline-bars span {
  display: block;
  width: 100%;
  min-height: 12px;
  border-radius: 8px 8px 0 0;
  background: var(--el-color-primary-light-3);
}

.personal-sparkline-bars small {
  color: var(--el-text-color-secondary);
}

.personal-issue-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.personal-mini-list {
  display: grid;
  gap: 10px;
}

.personal-mini-list p,
.personal-review-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0;
}

.personal-mini-list em {
  font-style: normal;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1200px) {
  .personal-workbench {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .personal-summary-grid,
  .personal-issue-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .personal-summary-grid,
  .personal-issue-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .personal-detail-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
