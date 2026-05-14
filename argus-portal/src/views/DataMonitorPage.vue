<template>
  <section class="page data-monitor-page">
    <div class="monitor-titlebar">
      <div>
        <p class="eyebrow">Database Observability</p>
        <h2>数据监控</h2>
        <p>按 appName 聚合慢 SQL、连接池风险、锁等待和接口日志表质量，配置来源绑定 SCM 应用映射。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <DataMonitorFilterBar :filters="filters" :loading="loading" @search="loadAll" @reset="resetFilters" />

    <DataMonitorStatCards :cards="statCards" />

    <el-alert
      v-if="errorMessage"
      class="monitor-alert"
      type="warning"
      show-icon
      :closable="false"
      :title="errorMessage"
    />

    <section class="panel-card monitor-table-panel">
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
            @detail="openSlowSqlDetail"
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
            @ignore="openActionDialog('logIssueIgnore', $event)"
          />
        </el-tab-pane>
      </el-tabs>
    </section>

    <SlowSqlDetailDrawer v-model="slowSqlDetailVisible" :row="selectedSlowSql" />

    <el-dialog v-model="actionDialog.visible" title="处理确认" width="420px">
      <el-form label-position="top">
        <el-form-item label="处理人">
          <el-input v-model.trim="actionDialog.operator" placeholder="operator" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model.trim="actionDialog.reason" type="textarea" :rows="3" placeholder="说明本次人工处理原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="actionSubmitting" @click="submitAction">确认</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import DataMonitorFilterBar from '../components/data-monitor/DataMonitorFilterBar.vue'
import DataMonitorStatCards from '../components/data-monitor/DataMonitorStatCards.vue'
import LogQualityIssueTable from '../components/data-monitor/LogQualityIssueTable.vue'
import PoolRiskTable from '../components/data-monitor/PoolRiskTable.vue'
import SlowSqlDetailDrawer from '../components/data-monitor/SlowSqlDetailDrawer.vue'
import SlowSqlTable from '../components/data-monitor/SlowSqlTable.vue'
import {
  buildMonitorStatCards,
  normalizeLogQualityIssue,
  normalizePoolRisk,
  normalizeSlowSqlEvent,
  unwrapSettledList,
  unwrapSettledValue,
} from '../api/dataMonitorAdapter'
import {
  fetchDataMonitorDashboard,
  fetchLogQualityIssues,
  fetchPoolRisks,
  fetchSlowSqlEvents,
  ignoreLogQualityIssue,
  ignoreSlowSql,
} from '../api/dataMonitor'

const filters = reactive({
  appName: '',
  environment: '',
  window: '24h',
})

const activeTab = ref('slowSql')
const loading = ref(false)
const errorMessage = ref('')
const dashboard = ref({})
const slowSqlEvents = ref([])
const poolRisks = ref([])
const logQualityIssues = ref([])
const selectedSlowSql = ref(null)
const slowSqlDetailVisible = ref(false)
const actionSubmitting = ref(false)
const actionDialog = reactive({
  visible: false,
  type: '',
  row: null,
  operator: '',
  reason: '',
})

const statCards = computed(() => [
  ...buildMonitorStatCards(dashboard.value, slowSqlEvents.value, poolRisks.value, logQualityIssues.value),
])

function queryParams() {
  return {
    appName: filters.appName || undefined,
    environment: filters.environment || undefined,
    window: filters.window,
  }
}

async function loadAll() {
  loading.value = true
  errorMessage.value = ''
  const params = queryParams()
  try {
    const [dashboardData, slowSqlData, poolData, logIssueData] = await Promise.allSettled([
      fetchDataMonitorDashboard(params),
      fetchSlowSqlEvents(params),
      fetchPoolRisks(params),
      fetchLogQualityIssues(params),
    ])
    dashboard.value = unwrapSettledValue(dashboardData, {})
    slowSqlEvents.value = unwrapSettledList(slowSqlData).map(normalizeSlowSqlEvent)
    poolRisks.value = unwrapSettledList(poolData).map(normalizePoolRisk)
    logQualityIssues.value = unwrapSettledList(logIssueData).map(normalizeLogQualityIssue)
    const rejected = [dashboardData, slowSqlData, poolData, logIssueData].find((item) => item.status === 'rejected')
    if (rejected) {
      errorMessage.value = '部分监控查询接口暂不可用，页面已保留为空结果。'
    }
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.appName = ''
  filters.environment = ''
  filters.window = '24h'
  loadAll()
}

function openSlowSqlDetail(row) {
  selectedSlowSql.value = row
  slowSqlDetailVisible.value = true
}

function openActionDialog(type, row) {
  actionDialog.visible = true
  actionDialog.type = type
  actionDialog.row = row
  actionDialog.operator = ''
  actionDialog.reason = ''
}

async function submitAction() {
  if (!actionDialog.operator) {
    ElMessage.warning('请填写处理人')
    return
  }
  actionSubmitting.value = true
  try {
    const payload = {
      operator: actionDialog.operator,
      reason: actionDialog.reason || null,
    }
    if (actionDialog.type === 'slowSqlIgnore') {
      await ignoreSlowSql(actionDialog.row.id, payload)
      ElMessage.success('慢 SQL 已忽略')
    } else if (actionDialog.type === 'logIssueIgnore') {
      await ignoreLogQualityIssue(actionDialog.row.id, payload)
      ElMessage.success('日志质量问题已忽略')
    }
    actionDialog.visible = false
    await loadAll()
  } catch (error) {
    ElMessage.error(error.message || '处理失败')
  } finally {
    actionSubmitting.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.data-monitor-page {
  gap: 18px;
}

.monitor-titlebar,
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.monitor-titlebar h2 {
  margin: 0;
  font-size: 28px;
  line-height: 1.15;
}

.monitor-titlebar p:last-child,
.section-heading p {
  margin: 10px 0 0;
  color: var(--muted);
  line-height: 1.7;
}

.monitor-table-panel {
  padding: 18px;
}

.cell-sub {
  display: block;
  color: var(--muted);
  font-size: 13px;
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

.cell-main {
  font-weight: 700;
  color: var(--text);
  line-height: 1.45;
}

.cell-sub {
  margin-top: 4px;
  line-height: 1.5;
}

@media (max-width: 1100px) {
  .monitor-titlebar,
  .section-heading {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
