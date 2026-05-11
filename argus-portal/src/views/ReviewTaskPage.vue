<template>
  <section class="page">
    <div class="page-banner compact">
      <div>
        <p class="eyebrow">Review Task Center</p>
        <h2>评审任务中心</h2>
        <p class="page-copy">
          统一查看代码评审任务的执行状态、评分结果、平台来源和最近进展。
          这是公司内部运维和研发负责人最常用的任务面板之一。
        </p>
      </div>
    </div>

    <section class="panel-card">
      <div class="section-heading">
        <div>
          <h3>任务筛选</h3>
          <p>按平台、状态和关键字快速定位需要关注的评审任务。</p>
        </div>
      </div>

      <el-form :inline="true" class="task-filter" @submit.prevent>
        <el-form-item label="平台">
          <el-select v-model="filters.scmProvider" clearable placeholder="全部平台" style="width: 160px">
            <el-option label="GitLab" value="gitlab" />
            <el-option label="GitHub" value="github" />
            <el-option label="Gitee" value="gitee" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 180px">
            <el-option label="PENDING" value="PENDING" />
            <el-option label="RUNNING" value="RUNNING" />
            <el-option label="DONE" value="DONE" />
            <el-option label="FAILED" value="FAILED" />
            <el-option label="TIMEOUT" value="TIMEOUT" />
          </el-select>
        </el-form-item>

        <el-form-item label="关键字">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="仓库名 / 标题 / 作者"
            style="width: 260px"
            @keyup.enter="loadTasks"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadTasks">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel-card">
      <div class="section-heading">
        <div>
          <h3>任务列表</h3>
          <p>默认展示最近更新的评审任务。</p>
        </div>
      </div>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
        class="task-alert"
      />

      <el-table :data="tasks" v-loading="loading" border class="task-table">
        <el-table-column prop="id" label="任务ID" min-width="90" />
        <el-table-column label="平台" min-width="110">
          <template #default="{ row }">
            <el-tag :type="providerTagType(row.scmProvider)" effect="light">
              {{ row.scmProvider || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="仓库 / 标题" min-width="320">
          <template #default="{ row }">
            <div class="cell-main">{{ row.projectName || composeRepo(row) }}</div>
            <div class="cell-sub">{{ row.mrTitle || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="authorName" label="提交者" min-width="120" />
        <el-table-column label="分支" min-width="180">
          <template #default="{ row }">
            <div class="cell-main">{{ row.sourceBranch || '-' }}</div>
            <div class="cell-sub">→ {{ row.targetBranch || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="dark">
              {{ row.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评分" min-width="110">
          <template #default="{ row }">
            <div class="cell-main">{{ row.totalScore ?? '-' }}</div>
            <div class="cell-sub">等级 {{ row.scoreLevel || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="问题统计" min-width="160">
          <template #default="{ row }">
            <div class="cell-sub">严重 {{ row.criticalCount || 0 }}</div>
            <div class="cell-sub">重要 {{ row.majorCount || 0 }} / 建议 {{ row.minorCount || 0 }}</div>
          </template>
        </el-table-column>
        <el-table-column label="最近更新时间" min-width="180">
          <template #default="{ row }">
            {{ formatDate(row.updateTime || row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="110">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="task-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pagination.pageNo"
          :page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-drawer
      v-model="detailVisible"
      title="评审任务详情"
      size="52%"
      destroy-on-close
    >
      <el-skeleton :rows="8" animated v-if="detailLoading" />

      <template v-else-if="selectedTask">
        <div class="detail-block">
          <h4>基础信息</h4>
          <div class="detail-grid">
            <div><span>任务ID</span><strong>{{ selectedTask.id }}</strong></div>
            <div><span>平台</span><strong>{{ selectedTask.scmProvider || '-' }}</strong></div>
            <div><span>仓库</span><strong>{{ selectedTask.projectName || composeRepo(selectedTask) }}</strong></div>
            <div><span>提交者</span><strong>{{ selectedTask.authorName || '-' }}</strong></div>
            <div><span>状态</span><strong>{{ selectedTask.status || '-' }}</strong></div>
            <div><span>评分</span><strong>{{ selectedTask.totalScore ?? '-' }} / {{ selectedTask.scoreLevel || '-' }}</strong></div>
          </div>
        </div>

        <div v-if="selectedTask.errorMessage" class="detail-block">
          <h4>失败原因</h4>
          <el-alert
            :title="selectedTask.errorMessage"
            type="error"
            :closable="false"
            show-icon
          />
        </div>

        <div class="detail-block">
          <h4>分支与链接</h4>
          <div class="detail-grid">
            <div><span>源分支</span><strong>{{ selectedTask.sourceBranch || '-' }}</strong></div>
            <div><span>目标分支</span><strong>{{ selectedTask.targetBranch || '-' }}</strong></div>
            <div><span>Commit SHA</span><strong>{{ selectedTask.lastCommitSha || '-' }}</strong></div>
            <div><span>评审链接</span><strong>{{ selectedTask.mrUrl || '-' }}</strong></div>
          </div>
        </div>

        <div class="detail-block">
          <h4>评审总结</h4>
          <el-input :model-value="selectedTask.summary || '暂无总结'" type="textarea" :rows="8" readonly />
        </div>

        <div class="detail-block">
          <h4>问题明细</h4>
          <el-empty v-if="!selectedIssues.length" description="当前任务暂无问题明细" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="issue in selectedIssues"
              :key="issue.id"
              :type="issueSeverityType(issue.severity)"
              :timestamp="`${issue.filePath || '-'} : ${issue.startLine || '-'}-${issue.endLine || '-'}行`"
            >
              <div class="issue-card">
                <strong>{{ issue.severity }} / {{ issue.category }}</strong>
                <p>{{ issue.description }}</p>
                <p class="cell-sub">建议：{{ issue.suggestion || '暂无' }}</p>
                <pre v-if="issue.codeSnippet" class="issue-code">{{ issue.codeSnippet }}</pre>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { fetchReviewTaskDetail, fetchReviewTasks } from '../api/review'

const loading = ref(false)
const errorMessage = ref('')
const tasks = ref([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedTask = ref(null)
const selectedIssues = ref([])

const filters = reactive({
  scmProvider: '',
  status: '',
  keyword: '',
})

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})

function composeRepo(row) {
  if (row.repoOwner && row.repoName) {
    return `${row.repoOwner}/${row.repoName}`
  }
  return row.repoName || row.projectName || '-'
}

function providerTagType(provider) {
  if (provider === 'gitlab') return 'warning'
  if (provider === 'github') return 'info'
  if (provider === 'gitee') return 'danger'
  return 'default'
}

function statusTagType(status) {
  if (status === 'DONE') return 'success'
  if (status === 'RUNNING') return 'primary'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  return 'warning'
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function issueSeverityType(severity) {
  if (severity === 'CRITICAL') return 'danger'
  if (severity === 'MAJOR') return 'warning'
  if (severity === 'MINOR') return 'primary'
  return 'success'
}

async function loadTasks() {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await fetchReviewTasks({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      scmProvider: filters.scmProvider || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
    })
    tasks.value = data.records || []
    pagination.total = data.total || 0
  } catch (error) {
    errorMessage.value = error.message || '加载评审任务失败'
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  selectedTask.value = null
  selectedIssues.value = []
  try {
    const data = await fetchReviewTaskDetail(row.id)
    selectedTask.value = data.task || row
    selectedIssues.value = data.issues || []
  } catch (error) {
    errorMessage.value = error.message || '加载任务详情失败'
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function resetFilters() {
  filters.scmProvider = ''
  filters.status = ''
  filters.keyword = ''
  pagination.pageNo = 1
  loadTasks()
}

function handlePageChange(pageNo) {
  pagination.pageNo = pageNo
  loadTasks()
}

function handleSizeChange(pageSize) {
  pagination.pageSize = pageSize
  pagination.pageNo = 1
  loadTasks()
}

onMounted(() => {
  loadTasks()
})
</script>
