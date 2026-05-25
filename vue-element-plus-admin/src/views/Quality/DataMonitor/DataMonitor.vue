<template>
  <ContentWrap>
    <div class="monitor-page">
      <section class="monitor-titlebar">
        <div>
          <p class="eyebrow">Database Observability</p>
          <h2>数据监控</h2>
          <p
            >按 appName 聚合慢
            SQL、连接池风险、锁等待和接口日志表质量，配置关系在独立配置页维护。</p
          >
        </div>
        <div class="titlebar-actions">
          <el-button @click="goConfigPage">监控配置</el-button>
          <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </section>

      <DataMonitorFilterBar
        :filters="filters"
        :loading="loading"
        @update:filters="applyFilters"
        @search="loadAll"
        @reset="resetFilters"
      />

      <DataMonitorStatCards :cards="statCards" />

      <el-alert
        v-if="errorMessage"
        class="monitor-alert"
        type="warning"
        show-icon
        :closable="false"
        :title="errorMessage"
      />

      <section class="monitor-panel">
        <div class="section-heading">
          <div>
            <h3>监控结果</h3>
            <p>索引建议只作为预警信息展示，Argus 不会在生产库执行 DDL。</p>
          </div>
          <el-tag effect="light">MySQL 5.7 · 只读采集</el-tag>
        </div>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="慢 SQL" name="slowSql">
            <SlowSqlTable
              :rows="slowSqlEvents"
              :loading="loading"
              :can-confirm="canConfirmSlowSql"
              :can-ignore="canIgnoreSlowSql"
              @detail="openSlowSqlDetail"
              @confirm="openActionDialog('slowSqlConfirm', $event)"
              @ignore="openActionDialog('slowSqlIgnore', $event)"
            />
          </el-tab-pane>

          <el-tab-pane label="连接池" name="pools">
            <PoolRiskTable :rows="poolRisks" :loading="loading" />
          </el-tab-pane>

          <el-tab-pane label="接口日志表质量" name="logQuality">
            <LogQualityIssueTable
              :rows="logQualityIssues"
              :loading="loading"
              :can-ignore="canIgnoreLogIssue"
              @ignore="openActionDialog('logIssueIgnore', $event)"
            />
          </el-tab-pane>
        </el-tabs>
      </section>

      <SlowSqlDetailDrawer v-model="slowSqlDetailVisible" :row="selectedSlowSql" />

      <el-dialog v-model="actionDialog.visible" :title="actionDialogTitle" width="460px">
        <el-form label-position="top">
          <el-form-item label="处理人">
            <el-input v-model.trim="actionDialog.operator" placeholder="operator" />
          </el-form-item>
          <el-form-item :label="actionDialog.type === 'slowSqlConfirm' ? '确认结论' : '原因'">
            <el-input
              v-model.trim="actionDialog.reason"
              type="textarea"
              :rows="3"
              :placeholder="
                actionDialog.type === 'slowSqlConfirm'
                  ? '填写人工确认后的结论'
                  : '说明本次人工处理原因'
              "
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="actionDialog.visible = false">取消</el-button>
          <el-button type="primary" :loading="actionSubmitting" @click="submitAction"
            >确认</el-button
          >
        </template>
      </el-dialog>
    </div>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElTabPane,
  ElTabs,
  ElTag
} from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { ContentWrap } from '@/components/ContentWrap'
import { hasPermi } from '@/components/Permission/src/utils'
import {
  confirmSlowSqlApi,
  fetchDataMonitorDashboardApi,
  fetchLogQualityIssuesApi,
  fetchPoolRisksApi,
  fetchSlowSqlEventsApi,
  ignoreLogQualityIssueApi,
  ignoreSlowSqlApi
} from '@/api/dataMonitor'
import {
  buildMonitorStatCards,
  normalizeLogQualityIssue,
  normalizePoolRisk,
  normalizeSlowSqlEvent
} from '@/api/dataMonitor/helper'
import type {
  DataMonitorDashboardItem,
  LogQualityIssueItem,
  PoolRiskItem,
  SlowSqlEventItem
} from '@/api/dataMonitor/types'
import DataMonitorFilterBar from './components/DataMonitorFilterBar.vue'
import DataMonitorStatCards from './components/DataMonitorStatCards.vue'
import LogQualityIssueTable from './components/LogQualityIssueTable.vue'
import PoolRiskTable from './components/PoolRiskTable.vue'
import SlowSqlDetailDrawer from './components/SlowSqlDetailDrawer.vue'
import SlowSqlTable from './components/SlowSqlTable.vue'

type ActionType = 'slowSqlIgnore' | 'slowSqlConfirm' | 'logIssueIgnore'

const router = useRouter()
const filters = reactive({
  appName: '',
  environment: '',
  window: '24h'
})

const activeTab = ref('slowSql')
const loading = ref(false)
const errorMessage = ref('')
const dashboard = ref<DataMonitorDashboardItem>({})
const slowSqlEvents = ref<Array<Record<string, any>>>([])
const poolRisks = ref<Array<Record<string, any>>>([])
const logQualityIssues = ref<Array<Record<string, any>>>([])
const selectedSlowSql = ref<Record<string, any> | null>(null)
const slowSqlDetailVisible = ref(false)
const actionSubmitting = ref(false)
const actionDialog = reactive<{
  visible: boolean
  type: ActionType
  row: Record<string, any> | null
  operator: string
  reason: string
}>({
  visible: false,
  type: 'slowSqlIgnore',
  row: null,
  operator: '',
  reason: ''
})

const statCards = computed(() =>
  buildMonitorStatCards(
    dashboard.value,
    slowSqlEvents.value as SlowSqlEventItem[],
    poolRisks.value as PoolRiskItem[],
    logQualityIssues.value as LogQualityIssueItem[]
  )
)

const actionDialogTitle = computed(() => {
  if (actionDialog.type === 'slowSqlConfirm') return '确认慢 SQL 结论'
  if (actionDialog.type === 'logIssueIgnore') return '忽略日志质量问题'
  return '忽略慢 SQL'
})

const canIgnoreSlowSql = computed(() => hasPermi('ignore'))
const canConfirmSlowSql = computed(() => hasPermi('confirm'))
const canIgnoreLogIssue = computed(() => hasPermi('ignore'))

const queryParams = () => ({
  appName: filters.appName || undefined,
  environment: filters.environment || undefined,
  window: filters.window
})

function toListData<T>(data: any): T[] {
  if (Array.isArray(data)) return data as T[]
  if (Array.isArray(data?.records)) return data.records as T[]
  if (Array.isArray(data?.items)) return data.items as T[]
  return []
}

const loadAll = async () => {
  loading.value = true
  errorMessage.value = ''
  const params = queryParams()
  try {
    const [dashboardRes, slowSqlRes, poolRes, logRes] = await Promise.allSettled([
      fetchDataMonitorDashboardApi(params),
      fetchSlowSqlEventsApi(params),
      fetchPoolRisksApi(params),
      fetchLogQualityIssuesApi(params)
    ])

    dashboard.value = dashboardRes.status === 'fulfilled' ? dashboardRes.value.data || {} : {}
    slowSqlEvents.value =
      slowSqlRes.status === 'fulfilled'
        ? toListData<SlowSqlEventItem>(slowSqlRes.value.data).map(normalizeSlowSqlEvent)
        : []
    poolRisks.value =
      poolRes.status === 'fulfilled'
        ? toListData<PoolRiskItem>(poolRes.value.data).map(normalizePoolRisk)
        : []
    logQualityIssues.value =
      logRes.status === 'fulfilled'
        ? toListData<LogQualityIssueItem>(logRes.value.data).map(normalizeLogQualityIssue)
        : []

    const rejected = [dashboardRes, slowSqlRes, poolRes, logRes].some(
      (item) => item.status === 'rejected'
    )
    if (rejected) {
      errorMessage.value = '部分监控查询接口暂不可用，页面已保留为空结果。'
    }
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.appName = ''
  filters.environment = ''
  filters.window = '24h'
  loadAll()
}

const applyFilters = (nextFilters: Record<string, any>) => {
  filters.appName = nextFilters.appName || ''
  filters.environment = nextFilters.environment || ''
  filters.window = nextFilters.window || '24h'
}

const goConfigPage = () => {
  router.push({ path: '/monitor-center/data-monitor-config' })
}

const openSlowSqlDetail = (row: Record<string, any>) => {
  selectedSlowSql.value = row
  slowSqlDetailVisible.value = true
}

const openActionDialog = (type: ActionType, row: Record<string, any>) => {
  actionDialog.visible = true
  actionDialog.type = type
  actionDialog.row = row
  actionDialog.operator = ''
  actionDialog.reason = ''
}

const submitAction = async () => {
  if (!actionDialog.operator) {
    ElMessage.warning('请填写处理人')
    return
  }
  if (!actionDialog.row?.id) {
    ElMessage.warning('当前未选中可处理的数据')
    return
  }
  actionSubmitting.value = true
  try {
    if (actionDialog.type === 'slowSqlIgnore') {
      await ignoreSlowSqlApi(actionDialog.row.id, {
        operator: actionDialog.operator,
        reason: actionDialog.reason || null
      })
      ElMessage.success('慢 SQL 已忽略')
    } else if (actionDialog.type === 'slowSqlConfirm') {
      await confirmSlowSqlApi(actionDialog.row.id, {
        operator: actionDialog.operator,
        confirmedConclusion: actionDialog.reason || null,
        confirmedCauseType: null,
        acceptedIndexSuggestion: false
      })
      ElMessage.success('慢 SQL 已确认')
    } else {
      await ignoreLogQualityIssueApi(actionDialog.row.id, {
        operator: actionDialog.operator,
        reason: actionDialog.reason || null
      })
      ElMessage.success('日志质量问题已忽略')
    }
    actionDialog.visible = false
    await loadAll()
  } catch (error: any) {
    ElMessage.error(error?.message || '处理失败')
  } finally {
    actionSubmitting.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.monitor-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.monitor-titlebar,
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.titlebar-actions {
  display: flex;
  gap: 12px;
}

.eyebrow {
  margin: 0 0 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-transform: uppercase;
}

.monitor-titlebar h2 {
  margin: 0;
  font-size: 28px;
  line-height: 1.15;
}

.monitor-titlebar p:last-child,
.section-heading p {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}

.monitor-panel {
  padding: 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.monitor-alert {
  margin: -4px 0 2px;
}

.section-heading {
  margin-bottom: 16px;
}

.section-heading h3 {
  margin: 0;
  font-size: 18px;
}
</style>
