<template>
  <section class="page quality-page">
    <div class="quality-titlebar">
      <div>
        <p class="eyebrow">Quality Intelligence</p>
        <h2>个人代码质量</h2>
        <p>
          基于 AI 评审结果沉淀提交者画像，跟踪团队质量趋势、个人短板和高频违规规则。
        </p>
      </div>
      <div class="quality-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <section class="panel-card quality-filter-panel">
      <div class="quality-filter-row">
        <el-segmented v-model="filters.scmProvider" :options="providerOptions" @change="handleProviderChange" />
        <el-select v-model="filters.days" style="width: 150px" @change="loadTopIssues">
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 30 天" :value="30" />
          <el-option label="近 90 天" :value="90" />
        </el-select>
        <el-select v-model="filters.sortBy" style="width: 180px">
          <el-option label="平均分从高到低" value="scoreDesc" />
          <el-option label="平均分从低到高" value="scoreAsc" />
          <el-option label="评审次数从多到少" value="reviewsDesc" />
          <el-option label="最近评审从近到远" value="recentDesc" />
        </el-select>
        <el-input
          v-model.trim="filters.keyword"
          clearable
          placeholder="搜索提交者 / authorId"
          style="width: 280px"
        />
      </div>
    </section>

    <div class="quality-stats">
      <article class="quality-stat">
        <span>已沉淀成员</span>
        <strong>{{ stats.memberCount }}</strong>
        <small>{{ providerLabel(filters.scmProvider) }} 画像</small>
      </article>
      <article class="quality-stat">
        <span>团队平均分</span>
        <strong>{{ stats.avgScore }}</strong>
        <small>{{ scoreLevel(stats.avgScore) }}</small>
      </article>
      <article class="quality-stat">
        <span>低分风险</span>
        <strong>{{ stats.riskCount }}</strong>
        <small>平均分低于 70</small>
      </article>
      <article class="quality-stat">
        <span>活跃成员</span>
        <strong>{{ stats.activeCount }}</strong>
        <small>近 {{ filters.days }} 天有评审</small>
      </article>
    </div>

    <div class="quality-workbench">
      <section class="panel-card quality-main-panel">
        <div class="section-heading">
          <div>
            <h3>人员画像</h3>
            <p>默认展示团队画像 Top 50，可按姓名、唯一 ID、分数和最近评审快速定位。</p>
          </div>
          <el-tag effect="light">{{ filteredProfiles.length }} 人</el-tag>
        </div>

        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
          class="quality-alert"
        />

        <el-table :data="filteredProfiles" v-loading="loading" border class="quality-table" @row-click="openProfile">
          <el-table-column label="提交者" min-width="230" fixed>
            <template #default="{ row }">
              <div class="person-cell">
                <span class="avatar-mark">{{ avatarText(row.authorName) }}</span>
                <div>
                  <strong>{{ row.authorName || '-' }}</strong>
                  <span>{{ row.authorId || '-' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="平台" min-width="110">
            <template #default="{ row }">
              <el-tag :type="providerTagType(row.scmProvider)" effect="light">
                {{ providerLabel(row.scmProvider) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="质量评分" min-width="160">
            <template #default="{ row }">
              <div class="score-cell">
                <strong>{{ formatScore(row.avgScore) }}</strong>
                <el-tag :type="scoreTagType(row.avgScore)" effect="light">{{ scoreLevel(row.avgScore) }}</el-tag>
              </div>
              <el-progress :percentage="scorePercent(row.avgScore)" :stroke-width="7" :show-text="false" />
            </template>
          </el-table-column>

          <el-table-column prop="totalReviews" label="评审次数" min-width="110" />

          <el-table-column label="维度短板" min-width="180">
            <template #default="{ row }">
              <div class="cell-main">{{ weakestDimension(row).label }}</div>
              <div class="cell-sub">{{ weakestDimension(row).score }}</div>
            </template>
          </el-table-column>

          <el-table-column label="高频问题" min-width="240">
            <template #default="{ row }">
              <div v-if="topRules(row).length" class="rule-tags">
                <el-tag v-for="item in topRules(row).slice(0, 2)" :key="item.rule" effect="plain">
                  {{ item.rule }} · {{ item.count }}
                </el-tag>
              </div>
              <span v-else class="cell-sub">暂无</span>
            </template>
          </el-table-column>

          <el-table-column label="最近评审" min-width="170">
            <template #default="{ row }">
              {{ formatDate(row.lastReviewAt) }}
            </template>
          </el-table-column>

          <el-table-column label="操作" fixed="right" width="110">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click.stop="openProfile(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && !filteredProfiles.length" description="暂无个人代码质量画像" />
      </section>

      <aside class="panel-card quality-side-panel">
        <div class="section-heading compact-heading">
          <div>
            <h3>团队高频问题</h3>
            <p>近 {{ filters.days }} 天聚合 Top 规则。</p>
          </div>
        </div>

        <el-skeleton v-if="topIssueLoading" :rows="6" animated />
        <el-empty v-else-if="!topIssues.length" description="暂无高频问题" />
        <div v-else class="top-issue-list">
          <button
            v-for="issue in topIssues"
            :key="issue.rule"
            type="button"
            :class="{ active: activeRule === issue.rule }"
            @click="toggleRule(issue.rule)"
          >
            <span>{{ issue.rule }}</span>
            <strong>{{ issue.count }}</strong>
            <el-progress :percentage="issuePercent(issue.count)" :stroke-width="6" :show-text="false" />
          </button>
        </div>
      </aside>
    </div>

    <el-drawer
      v-model="detailVisible"
      title="个人代码质量详情"
      size="860px"
      destroy-on-close
      class="profile-drawer"
    >
      <el-skeleton v-if="detailLoading" :rows="8" animated />

      <template v-else-if="selectedProfile">
        <div class="profile-hero">
          <span class="profile-avatar">{{ avatarText(selectedProfile.authorName) }}</span>
          <div>
            <p>{{ providerLabel(selectedProfile.scmProvider) }}</p>
            <h3>{{ selectedProfile.authorName || '-' }}</h3>
            <span>{{ selectedProfile.authorId || '-' }}</span>
          </div>
          <strong>{{ formatScore(selectedProfile.avgScore) }}</strong>
        </div>

        <div class="detail-block">
          <h4>基础画像</h4>
          <div class="detail-grid">
            <div><span>累计评审</span><strong>{{ selectedProfile.totalReviews || 0 }} 次</strong></div>
            <div><span>平均等级</span><strong>{{ scoreLevel(selectedProfile.avgScore) }}</strong></div>
            <div><span>首次评审</span><strong>{{ formatDate(selectedProfile.firstReviewAt) }}</strong></div>
            <div><span>最近评审</span><strong>{{ formatDate(selectedProfile.lastReviewAt) }}</strong></div>
          </div>
        </div>

        <div class="detail-block">
          <h4>五维能力</h4>
          <div class="dimension-list">
            <div v-for="item in dimensionItems(selectedProfile)" :key="item.key">
              <span>{{ item.label }}</span>
              <strong>{{ item.score }}</strong>
              <el-progress :percentage="item.percent" :stroke-width="8" :show-text="false" />
            </div>
          </div>
        </div>

        <div class="detail-block">
          <h4>分数趋势</h4>
          <el-empty v-if="!detailTrend.length" description="暂无趋势数据" />
          <div v-else class="sparkline-bars">
            <div v-for="(score, index) in detailTrend" :key="index">
              <span :style="{ height: `${Math.max(12, score)}%` }"></span>
              <small>{{ score }}</small>
            </div>
          </div>
        </div>

        <div class="detail-block">
          <h4>高频问题</h4>
          <el-empty v-if="!topRules(selectedProfile).length && !topTags(selectedProfile).length" description="暂无高频问题" />
          <div v-else class="profile-issue-grid">
            <div>
              <span class="mini-title">规则</span>
              <p v-for="item in topRules(selectedProfile)" :key="item.rule">
                <strong>{{ item.rule }}</strong><em>{{ item.count }}</em>
              </p>
            </div>
            <div>
              <span class="mini-title">标签</span>
              <p v-for="item in topTags(selectedProfile)" :key="item.tag">
                <strong>{{ item.tag }}</strong><em>{{ item.count }}</em>
              </p>
            </div>
          </div>
        </div>

        <div class="detail-block">
          <h4>最近评审</h4>
          <el-empty v-if="!recentReviews(selectedProfile).length" description="暂无最近评审摘要" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="item in recentReviews(selectedProfile)"
              :key="item.taskId || `${item.projectName}-${item.reviewedAt}`"
              :timestamp="formatDate(item.reviewedAt || item.createTime)"
            >
              <div class="recent-review-card">
                <strong>{{ item.projectName || item.repoName || '未知仓库' }}</strong>
                <span>{{ item.score ?? '-' }} / {{ item.scoreLevel || '-' }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, View } from '@element-plus/icons-vue'
import {
  fetchReviewerProfile,
  fetchReviewerProfiles,
  fetchReviewerTrend,
  fetchTeamTopIssues,
} from '../api/profile'

const providerOptions = [
  { label: 'GitHub', value: 'github' },
  { label: 'GitLab', value: 'gitlab' },
  { label: 'Gitee', value: 'gitee' },
]

const dimensionLabels = {
  compliance: '规范合规',
  correctness: '逻辑正确',
  dataSafety: '数据安全',
  performance: '性能风险',
  maintainability: '可维护性',
}

const loading = ref(false)
const topIssueLoading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const profiles = ref([])
const topIssues = ref([])
const activeRule = ref('')
const detailVisible = ref(false)
const selectedProfile = ref(null)
const detailTrend = ref([])

const filters = reactive({
  scmProvider: 'github',
  days: 30,
  keyword: '',
  sortBy: 'scoreDesc',
})

const enrichedProfiles = computed(() => profiles.value.map((profile) => ({
  ...profile,
  _dimensionStats: parseJson(profile.dimensionStats, {}),
  _topIssueRules: parseJson(profile.topIssueRules, []),
  _topIssueTags: parseJson(profile.topIssueTags, []),
  _scoreTrend: parseJson(profile.scoreTrend, []),
  _recentReviews: parseJson(profile.recentReviews, []),
})))

const filteredProfiles = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  let rows = enrichedProfiles.value.filter((profile) => {
    const text = `${profile.authorName || ''} ${profile.authorId || ''}`.toLowerCase()
    const matchesKeyword = !keyword || text.includes(keyword)
    const matchesRule = !activeRule.value || topRules(profile).some((item) => item.rule === activeRule.value)
    return matchesKeyword && matchesRule
  })

  rows = [...rows].sort((a, b) => {
    if (filters.sortBy === 'scoreAsc') return numericScore(a.avgScore) - numericScore(b.avgScore)
    if (filters.sortBy === 'reviewsDesc') return (b.totalReviews || 0) - (a.totalReviews || 0)
    if (filters.sortBy === 'recentDesc') return dateValue(b.lastReviewAt) - dateValue(a.lastReviewAt)
    return numericScore(b.avgScore) - numericScore(a.avgScore)
  })
  return rows
})

const stats = computed(() => {
  const rows = enrichedProfiles.value
  const scoreRows = rows.filter((item) => item.avgScore !== null && item.avgScore !== undefined)
  const avgScore = scoreRows.length
    ? Math.round(scoreRows.reduce((sum, item) => sum + numericScore(item.avgScore), 0) / scoreRows.length)
    : 0
  return {
    memberCount: rows.length,
    avgScore,
    riskCount: rows.filter((item) => numericScore(item.avgScore) > 0 && numericScore(item.avgScore) < 70).length,
    activeCount: rows.filter((item) => isWithinDays(item.lastReviewAt, filters.days)).length,
  }
})

async function loadAll() {
  await Promise.all([loadProfiles(), loadTopIssues()])
}

async function loadProfiles() {
  loading.value = true
  errorMessage.value = ''
  try {
    profiles.value = await fetchReviewerProfiles({ scmProvider: filters.scmProvider }) || []
  } catch (error) {
    errorMessage.value = error.message || '加载个人代码质量画像失败'
  } finally {
    loading.value = false
  }
}

async function loadTopIssues() {
  topIssueLoading.value = true
  try {
    topIssues.value = await fetchTeamTopIssues({
      scmProvider: filters.scmProvider,
      days: filters.days,
    }) || []
  } catch (error) {
    errorMessage.value = error.message || '加载团队高频问题失败'
  } finally {
    topIssueLoading.value = false
  }
}

function handleProviderChange() {
  activeRule.value = ''
  loadAll()
}

async function openProfile(row) {
  detailVisible.value = true
  detailLoading.value = true
  selectedProfile.value = null
  detailTrend.value = []
  try {
    const [profile, trend] = await Promise.all([
      fetchReviewerProfile(row.authorName, { scmProvider: row.scmProvider || filters.scmProvider }),
      fetchReviewerTrend(row.authorName, { scmProvider: row.scmProvider || filters.scmProvider }),
    ])
    selectedProfile.value = {
      ...row,
      ...(profile || {}),
    }
    detailTrend.value = Array.isArray(trend) ? trend : parseJson(selectedProfile.value.scoreTrend, [])
  } catch (error) {
    errorMessage.value = error.message || '加载个人画像详情失败'
    selectedProfile.value = row
    detailTrend.value = parseJson(row.scoreTrend, [])
  } finally {
    detailLoading.value = false
  }
}

function toggleRule(rule) {
  activeRule.value = activeRule.value === rule ? '' : rule
}

function parseJson(value, fallback) {
  if (!value) return fallback
  if (Array.isArray(value) || typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

function providerLabel(provider) {
  if (provider === 'gitlab') return 'GitLab'
  if (provider === 'github') return 'GitHub'
  if (provider === 'gitee') return 'Gitee'
  return provider || '-'
}

function providerTagType(provider) {
  if (provider === 'gitlab') return 'warning'
  if (provider === 'github') return 'info'
  if (provider === 'gitee') return 'danger'
  return 'primary'
}

function numericScore(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function formatScore(value) {
  const score = numericScore(value)
  return score ? score.toFixed(0) : '-'
}

function scorePercent(value) {
  return Math.max(0, Math.min(100, Math.round(numericScore(value))))
}

function scoreLevel(value) {
  const score = numericScore(value)
  if (!score) return '暂无'
  if (score >= 90) return 'A'
  if (score >= 80) return 'B'
  if (score >= 70) return 'C'
  return 'Risk'
}

function scoreTagType(value) {
  const score = numericScore(value)
  if (!score) return 'info'
  if (score >= 90) return 'success'
  if (score >= 80) return 'primary'
  if (score >= 70) return 'warning'
  return 'danger'
}

function topRules(profile) {
  return normalizeIssueItems(profile._topIssueRules || parseJson(profile.topIssueRules, []), 'rule')
}

function topTags(profile) {
  return normalizeIssueItems(profile._topIssueTags || parseJson(profile.topIssueTags, []), 'tag')
}

function normalizeIssueItems(items, keyName) {
  if (!Array.isArray(items)) return []
  return items
    .map((item) => ({
      [keyName]: item[keyName] || item.name || item.rule || item.tag || '未命名',
      count: Number(item.count || item.value || 0),
    }))
    .filter((item) => item[keyName])
}

function weakestDimension(profile) {
  const items = dimensionItems(profile).filter((item) => item.score !== '-')
  if (!items.length) return { label: '暂无', score: '-' }
  return [...items].sort((a, b) => Number(a.score) - Number(b.score))[0]
}

function dimensionItems(profile) {
  const data = profile._dimensionStats || parseJson(profile.dimensionStats, {})
  return Object.entries(dimensionLabels).map(([key, label]) => {
    const score = data[key]
    const normalized = Number(score)
    return {
      key,
      label,
      score: Number.isFinite(normalized) ? normalized.toFixed(0) : '-',
      percent: Number.isFinite(normalized) ? Math.max(0, Math.min(100, Math.round(normalized))) : 0,
    }
  })
}

function recentReviews(profile) {
  const items = profile._recentReviews || parseJson(profile.recentReviews, [])
  return Array.isArray(items) ? items : []
}

function issuePercent(count) {
  const max = Math.max(...topIssues.value.map((item) => Number(item.count || 0)), 1)
  return Math.round((Number(count || 0) / max) * 100)
}

function avatarText(name) {
  if (!name) return 'U'
  return String(name).trim().slice(0, 1).toUpperCase()
}

function dateValue(value) {
  if (!value) return 0
  const time = new Date(value).getTime()
  return Number.isFinite(time) ? time : 0
}

function isWithinDays(value, days) {
  const time = dateValue(value)
  if (!time) return false
  return Date.now() - time <= days * 24 * 60 * 60 * 1000
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.quality-page {
  gap: 18px;
}

.quality-titlebar,
.quality-stats,
.quality-workbench {
  min-width: 0;
}

.quality-titlebar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 26px 28px;
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  background: var(--surface);
  box-shadow: var(--shadow);
}

.quality-titlebar h2 {
  margin: 0;
  font-size: 32px;
}

.quality-titlebar p:last-child {
  margin: 12px 0 0;
  color: var(--muted);
  line-height: 1.7;
}

.quality-actions {
  display: flex;
  gap: 10px;
}

.quality-filter-panel {
  padding: 18px 20px;
}

.quality-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.quality-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.quality-stat {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.quality-stat span,
.quality-stat small {
  display: block;
  color: var(--muted);
}

.quality-stat strong {
  display: block;
  margin: 8px 0 6px;
  font-size: 32px;
  line-height: 1;
}

.quality-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 18px;
}

.quality-main-panel,
.quality-side-panel {
  min-width: 0;
}

.quality-alert {
  margin-bottom: 14px;
}

.person-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.avatar-mark,
.profile-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  color: #fff;
  background: linear-gradient(135deg, #2563eb, #0f9f67);
  font-weight: 800;
}

.avatar-mark {
  width: 36px;
  height: 36px;
  border-radius: 12px;
}

.person-cell strong,
.person-cell span {
  display: block;
}

.person-cell span {
  color: var(--muted);
  font-size: 12px;
  word-break: break-all;
}

.score-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.score-cell strong {
  font-size: 22px;
}

.rule-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.compact-heading {
  margin-bottom: 12px;
}

.top-issue-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.top-issue-list button {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  width: 100%;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-soft);
  color: var(--text);
  text-align: left;
  cursor: pointer;
}

.top-issue-list button.active {
  border-color: rgba(37, 99, 235, 0.45);
  background: #eef5ff;
}

.top-issue-list span {
  min-width: 0;
  font-weight: 700;
  word-break: break-word;
}

.top-issue-list .el-progress {
  grid-column: 1 / -1;
}

.profile-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  margin-bottom: 28px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface-soft);
}

.profile-avatar {
  width: 58px;
  height: 58px;
  border-radius: 18px;
  font-size: 22px;
}

.profile-hero p,
.profile-hero h3 {
  margin: 0;
}

.profile-hero p,
.profile-hero span {
  color: var(--muted);
}

.profile-hero h3 {
  margin: 4px 0;
  font-size: 22px;
}

.profile-hero > strong {
  font-size: 34px;
}

.dimension-list {
  display: grid;
  gap: 12px;
}

.dimension-list div {
  display: grid;
  grid-template-columns: 120px 48px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.dimension-list span {
  color: var(--muted);
  font-weight: 700;
}

.sparkline-bars {
  display: flex;
  align-items: end;
  gap: 10px;
  min-height: 150px;
  padding: 16px 12px 8px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface-soft);
}

.sparkline-bars div {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: end;
  gap: 8px;
  min-width: 18px;
  height: 132px;
}

.sparkline-bars span {
  width: 100%;
  max-width: 28px;
  border-radius: 8px 8px 2px 2px;
  background: linear-gradient(180deg, #2563eb, #0f9f67);
}

.sparkline-bars small {
  color: var(--muted);
  font-size: 11px;
}

.profile-issue-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.profile-issue-grid > div,
.recent-review-card {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface-soft);
}

.mini-title {
  display: block;
  margin-bottom: 10px;
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.profile-issue-grid p {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin: 0 0 8px;
}

.profile-issue-grid strong {
  word-break: break-word;
}

.profile-issue-grid em {
  color: var(--primary);
  font-style: normal;
  font-weight: 800;
}

.recent-review-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.recent-review-card span {
  color: var(--primary);
  font-weight: 800;
}

@media (max-width: 1360px) {
  .quality-workbench {
    grid-template-columns: 1fr;
  }

  .quality-side-panel {
    order: -1;
  }
}

@media (max-width: 1024px) {
  .quality-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .quality-titlebar {
    flex-direction: column;
    padding: 20px;
  }

  .quality-stats,
  .profile-issue-grid {
    grid-template-columns: 1fr;
  }

  .dimension-list div {
    grid-template-columns: 1fr;
  }

  .profile-hero {
    grid-template-columns: 1fr;
  }
}
</style>
