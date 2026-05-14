<template>
  <section class="page monitor-config-page">
    <div class="page-titlebar">
      <div>
        <p class="eyebrow">Data Monitor Config</p>
        <h2>数据监控配置</h2>
        <p>按应用维护数据库监控配置；SCM 只提供 appName 与仓库映射。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadOptions">刷新</el-button>
    </div>

    <section class="panel-card query-panel">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="应用">
              <el-select v-model="selectedMappingId" filterable placeholder="选择 appName" style="width: 100%">
                <el-option
                  v-for="item in mappingOptions"
                  :key="item.id"
                  :label="`${item.appName} · ${item.scmProvider || '-'}:${item.scmProjectId || '-'}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="SCM 仓库">
              <el-input :model-value="selectedScmConfig?.projectName || composeRepoName(selectedScmConfig) || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="服务源码根">
              <el-input :model-value="selectedMapping?.sourceRoot || '-'" readonly />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </section>

    <el-empty v-if="!selectedMapping" description="请选择应用后维护数据监控配置" />

    <template v-else>
      <section class="panel-card">
        <div class="section-title">
          <div>
            <h3>监控总配置</h3>
            <p>应用级总开关、负责人、默认频率和告警模式。</p>
          </div>
          <el-button type="primary" :loading="saving" @click="saveMonitorOverview">保存总配置</el-button>
        </div>
        <el-form label-position="top">
          <el-row :gutter="16">
            <el-col :span="4">
              <el-form-item label="启用监控">
                <el-switch v-model="monitorForm.enabled" active-text="启用" inactive-text="停用" />
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="负责团队">
                <el-input v-model.trim="monitorForm.ownerTeam" placeholder="例如 交易平台" />
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="技术负责人">
                <el-input v-model.trim="monitorForm.techOwner" placeholder="例如 zhangsan" />
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="告警 Webhook">
                <el-select v-model="monitorForm.alertWebhookMode" style="width: 100%">
                  <el-option label="沿用 SCM Webhook" value="SCM_CONFIG" />
                  <el-option label="仅记录不通知" value="NONE" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="告警扫描秒数">
                <el-input-number v-model="monitorForm.alertScanIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="默认运行态采集秒数">
                <el-input-number v-model="monitorForm.defaultRuntimeCollectIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="默认连接池推送秒数">
                <el-input-number v-model="monitorForm.defaultPoolMetricPushIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="默认日志巡检秒数">
                <el-input-number v-model="monitorForm.defaultLogQualityCheckIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="备注">
                <el-input v-model.trim="monitorForm.remark" placeholder="接入范围或注意事项" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </section>

      <section class="panel-card">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="只读数据源" name="datasource">
            <div class="tab-toolbar">
              <div>
                <h3>只读数据源</h3>
                <p>用于采集 processlist、事务、锁等待、QPS 与 EXPLAIN。</p>
              </div>
              <el-button type="primary" @click="openDatasourceDialog()">新增数据源</el-button>
            </div>
            <el-table :data="dataSources" v-loading="configLoading" border>
              <el-table-column prop="datasourceCode" label="编码" min-width="140" />
              <el-table-column label="库" min-width="240">
                <template #default="{ row }">
                  <div class="table-main">{{ row.databaseName || '-' }}</div>
                  <div class="table-sub">{{ row.jdbcUrl || '-' }}</div>
                </template>
              </el-table-column>
              <el-table-column prop="username" label="账号" width="150" />
              <el-table-column label="频率" width="170">
                <template #default="{ row }">
                  <div class="table-sub">运行态 {{ row.runtimeCollectIntervalSeconds ?? '-' }}s</div>
                  <div class="table-sub">连接池 {{ row.poolMetricPushIntervalSeconds ?? '-' }}s</div>
                </template>
              </el-table-column>
              <el-table-column label="能力" min-width="220">
                <template #default="{ row }">
                  <el-tag v-if="row.readonly !== false" size="small" type="success" effect="plain">只读</el-tag>
                  <el-tag v-if="row.collectProcesslist" size="small" effect="plain">PROCESSLIST</el-tag>
                  <el-tag v-if="row.collectGlobalStatus" size="small" effect="plain">QPS</el-tag>
                  <el-tag v-if="row.explainEnabled" size="small" effect="plain">EXPLAIN</el-tag>
                  <el-tag v-if="row.fullSqlCollectEnabled" size="small" effect="plain">完整 SQL</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-switch :model-value="Boolean(row.enabled)" @change="toggleDatasource(row)" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openDatasourceDialog(row)">编辑</el-button>
                  <el-button link type="primary" :loading="testingDatasourceId === row.id" @click="testSavedDatasource(row)">测试连接</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="Slow Log" name="slowLog">
            <el-empty description="Slow Log 配置将按数据源维护；下一步迁移为标准弹窗表单" />
          </el-tab-pane>

          <el-tab-pane label="接口日志表" name="logTable">
            <el-table :data="logTables" v-loading="configLoading" border>
              <el-table-column prop="configName" label="配置名称" min-width="160" />
              <el-table-column prop="tableName" label="表名" min-width="160" />
              <el-table-column prop="scanMode" label="扫描模式" width="140" />
              <el-table-column prop="qualityCheckIntervalSeconds" label="巡检秒数" width="120" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-switch :model-value="Boolean(row.enabled)" @change="toggleLogTable(row)" />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </section>
    </template>

    <el-dialog
      v-model="datasourceDialogVisible"
      :title="editingDatasourceId ? '编辑数据源' : '新增数据源'"
      width="860px"
      destroy-on-close
      @closed="resetDatasourceForm"
    >
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="数据源编码" required>
              <el-input v-model.trim="datasourceForm.datasourceCode" placeholder="order-main" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="显示名称">
              <el-input v-model.trim="datasourceForm.datasourceName" placeholder="订单主库" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库名">
              <el-input v-model.trim="datasourceForm.databaseName" placeholder="database" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="JDBC URL" required>
          <el-input v-model.trim="datasourceForm.jdbcUrl" placeholder="jdbc:mysql://host:3306/db?useSSL=false" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="用户名" required>
              <el-input v-model.trim="datasourceForm.username" placeholder="readonly_user" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="editingDatasourceId ? '密码（留空不修改）' : '密码'" :required="!editingDatasourceId">
              <el-input v-model.trim="datasourceForm.password" type="password" show-password placeholder="只读账号密码" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="数据库版本">
              <el-select v-model="datasourceForm.dbVersion" style="width: 100%">
                <el-option label="MySQL 5.7" value="5.7" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="启用">
              <el-switch v-model="datasourceForm.enabled" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="运行态采集秒数">
              <el-input-number v-model="datasourceForm.runtimeCollectIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="连接池推送秒数">
              <el-input-number v-model="datasourceForm.poolMetricPushIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="长 SQL 秒数">
              <el-input-number v-model="datasourceForm.thresholds.longSqlSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="连接使用率 %">
              <el-input-number v-model="datasourceForm.thresholds.connectionUsagePercent" :min="1" :max="100" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="采集能力">
          <el-checkbox v-model="datasourceForm.collectOptions.processlist">PROCESSLIST</el-checkbox>
          <el-checkbox v-model="datasourceForm.collectOptions.innodbTransaction">InnoDB 事务</el-checkbox>
          <el-checkbox v-model="datasourceForm.collectOptions.innodbLock">InnoDB 锁等待</el-checkbox>
          <el-checkbox v-model="datasourceForm.collectOptions.globalStatus">GLOBAL STATUS / QPS</el-checkbox>
          <el-checkbox v-model="datasourceForm.collectOptions.explain">EXPLAIN</el-checkbox>
          <el-checkbox v-model="datasourceForm.collectOptions.fullSql">完整 SQL</el-checkbox>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="datasourceTestResult"
        class="test-result"
        :type="datasourceTestResult.connected && datasourceTestResult.readonlyVerified ? 'success' : 'warning'"
        show-icon
        :closable="false"
        :title="datasourceTestResult.message || '只读连通性测试完成'"
      />
      <template #footer>
        <el-button @click="datasourceDialogVisible = false">取消</el-button>
        <el-button :loading="datasourceTestLoading" @click="testCurrentDatasource">测试连接</el-button>
        <el-button type="primary" :loading="saving" @click="saveDatasource">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { fetchProjectMappings, fetchScmConfigs } from '../api/scm'
import {
  createDataSource,
  fetchDataMonitorOverview,
  fetchDataSources,
  fetchLogTables,
  saveDataMonitorOverview,
  setDataSourceEnabled,
  setLogTableEnabled,
  testDataSource,
  testExistingDataSource,
  updateDataSource,
} from '../api/dataMonitor'

const route = useRoute()
const loading = ref(false)
const configLoading = ref(false)
const saving = ref(false)
const scmConfigs = ref([])
const projectMappings = ref([])
const selectedMappingId = ref(null)
const activeTab = ref('datasource')
const datasourceDialogVisible = ref(false)
const datasourceTestLoading = ref(false)
const datasourceTestResult = ref(null)
const testingDatasourceId = ref(null)
const dataSources = ref([])
const logTables = ref([])
const editingDatasourceId = ref(null)

const monitorForm = reactive(createMonitorForm())
const datasourceForm = reactive(createDatasourceForm())

const mappingOptions = computed(() => projectMappings.value.filter((item) => findScmConfig(item)))
const selectedMapping = computed(() => mappingOptions.value.find((item) => item.id === selectedMappingId.value) || null)
const selectedScmConfig = computed(() => selectedMapping.value ? findScmConfig(selectedMapping.value) : null)

watch(selectedMappingId, async () => {
  resetPageForms()
  if (selectedMapping.value) {
    await loadDataMonitorConfig()
  }
})

onMounted(loadOptions)

async function loadOptions() {
  loading.value = true
  try {
    const [configs, mappings] = await Promise.all([fetchScmConfigs(), fetchProjectMappings()])
    scmConfigs.value = configs || []
    projectMappings.value = mappings || []
    const queryMappingId = Number(route.query.mappingId || 0)
    const nextMapping = mappingOptions.value.find((item) => item.id === queryMappingId) || mappingOptions.value[0]
    selectedMappingId.value = nextMapping?.id || null
  } catch (error) {
    ElMessage.error(error.message || '加载数据监控配置失败')
  } finally {
    loading.value = false
  }
}

async function loadDataMonitorConfig() {
  if (!selectedMapping.value || !currentScmConfigId()) return
  configLoading.value = true
  try {
    const [overview, datasourceList, logTableList] = await Promise.all([
      fetchDataMonitorOverview(currentScmConfigId(), selectedMapping.value.id),
      fetchDataSources(currentScmConfigId(), selectedMapping.value.id),
      fetchLogTables(currentScmConfigId(), selectedMapping.value.id),
    ])
    Object.assign(monitorForm, {
      enabled: overview?.enabled !== false,
      ownerTeam: overview?.ownerTeam || '',
      techOwner: overview?.techOwner || '',
      alertWebhookMode: overview?.alertWebhookMode || 'SCM_CONFIG',
      defaultRuntimeCollectIntervalSeconds: overview?.defaultRuntimeCollectIntervalSeconds ?? 30,
      defaultPoolMetricPushIntervalSeconds: overview?.defaultPoolMetricPushIntervalSeconds ?? 30,
      defaultLogQualityCheckIntervalSeconds: overview?.defaultLogQualityCheckIntervalSeconds ?? 300,
      alertScanIntervalSeconds: overview?.alertScanIntervalSeconds ?? 60,
      remark: overview?.remark || '',
    })
    dataSources.value = datasourceList || []
    logTables.value = logTableList || []
  } catch (error) {
    ElMessage.error(error.message || '加载应用监控配置失败')
  } finally {
    configLoading.value = false
  }
}

async function saveMonitorOverview() {
  if (!selectedMapping.value || !currentScmConfigId()) return
  saving.value = true
  try {
    await saveDataMonitorOverview(currentScmConfigId(), selectedMapping.value.id, {
      enabled: monitorForm.enabled,
      ownerTeam: monitorForm.ownerTeam || null,
      techOwner: monitorForm.techOwner || null,
      alertWebhookMode: monitorForm.alertWebhookMode || 'SCM_CONFIG',
      defaultRuntimeCollectIntervalSeconds: monitorForm.defaultRuntimeCollectIntervalSeconds,
      defaultPoolMetricPushIntervalSeconds: monitorForm.defaultPoolMetricPushIntervalSeconds,
      defaultLogQualityCheckIntervalSeconds: monitorForm.defaultLogQualityCheckIntervalSeconds,
      alertScanIntervalSeconds: monitorForm.alertScanIntervalSeconds,
      remark: monitorForm.remark || null,
    })
    ElMessage.success('监控总配置已保存')
    await loadDataMonitorConfig()
  } catch (error) {
    ElMessage.error(error.message || '保存监控总配置失败')
  } finally {
    saving.value = false
  }
}

function openDatasourceDialog(row) {
  datasourceTestResult.value = null
  if (row) {
    editingDatasourceId.value = row.id
    Object.assign(datasourceForm, {
      datasourceCode: row.datasourceCode || '',
      datasourceName: row.datasourceName || '',
      dbType: String(row.dbType || 'MYSQL').toLowerCase(),
      dbVersion: row.dbVersion || '5.7',
      jdbcUrl: row.jdbcUrl || '',
      databaseName: row.databaseName || '',
      username: row.username || '',
      password: '',
      readonly: row.readonly !== false,
      enabled: row.enabled !== false,
      runtimeCollectIntervalSeconds: row.runtimeCollectIntervalSeconds ?? 30,
      poolMetricPushIntervalSeconds: row.poolMetricPushIntervalSeconds ?? 30,
      thresholds: {
        longSqlSeconds: row.thresholds?.longSqlSeconds ?? 10,
        longTransactionSeconds: row.thresholds?.longTransactionSeconds ?? 30,
        lockWaitSeconds: row.thresholds?.lockWaitSeconds ?? 5,
        connectionUsagePercent: row.thresholds?.connectionUsagePercent ?? 80,
      },
      collectOptions: {
        processlist: row.collectProcesslist !== false,
        innodbTransaction: row.collectInnodbTrx !== false,
        innodbLock: row.collectInnodbLock !== false,
        globalStatus: row.collectGlobalStatus !== false,
        explain: row.explainEnabled !== false,
        fullSql: row.fullSqlCollectEnabled !== false,
      },
    })
  } else {
    resetDatasourceForm()
  }
  datasourceDialogVisible.value = true
}

async function testCurrentDatasource() {
  if (!selectedMapping.value || !currentScmConfigId()) return
  datasourceTestLoading.value = true
  datasourceTestResult.value = null
  try {
    if (editingDatasourceId.value) {
      if (!datasourceForm.jdbcUrl || !datasourceForm.username) {
        ElMessage.warning('请填写 JDBC URL 和只读用户名后再测试')
        return
      }
      datasourceTestResult.value = await testExistingDataSource(
        currentScmConfigId(),
        selectedMapping.value.id,
        editingDatasourceId.value,
        {
          jdbcUrl: datasourceForm.jdbcUrl,
          username: datasourceForm.username,
          password: datasourceForm.password || null,
        },
      )
    } else {
      if (!datasourceForm.jdbcUrl || !datasourceForm.username || !datasourceForm.password) {
        ElMessage.warning('请填写 JDBC URL、只读用户名和密码后再测试')
        return
      }
      datasourceTestResult.value = await testDataSource(currentScmConfigId(), selectedMapping.value.id, {
        jdbcUrl: datasourceForm.jdbcUrl,
        username: datasourceForm.username,
        password: datasourceForm.password,
      })
    }
    ElMessage.success('连接测试完成')
  } catch (error) {
    datasourceTestResult.value = {
      connected: false,
      readonlyVerified: false,
      canExplain: false,
      canReadProcesslist: false,
      canReadInnodbStatus: false,
      message: error.message || '连接测试失败',
    }
    ElMessage.error(error.message || '连接测试失败')
  } finally {
    datasourceTestLoading.value = false
  }
}

async function testSavedDatasource(row) {
  if (!selectedMapping.value || !currentScmConfigId()) return
  testingDatasourceId.value = row.id
  try {
    const result = await testExistingDataSource(currentScmConfigId(), selectedMapping.value.id, row.id)
    if (result.connected && result.readonlyVerified) {
      ElMessage.success(result.message || '数据源连接测试通过')
    } else {
      ElMessage.warning(result.message || '数据源连接测试未完全通过')
    }
  } catch (error) {
    ElMessage.error(error.message || '数据源连接测试失败')
  } finally {
    testingDatasourceId.value = null
  }
}

async function saveDatasource() {
  if (!selectedMapping.value || !currentScmConfigId()) return
  const validationMessage = validateDatasourceForm()
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }
  saving.value = true
  try {
    const payload = {
      ...datasourceForm,
      datasourceName: datasourceForm.datasourceName || datasourceForm.datasourceCode,
    }
    if (editingDatasourceId.value) {
      await updateDataSource(currentScmConfigId(), selectedMapping.value.id, editingDatasourceId.value, payload)
      ElMessage.success('数据源已保存')
    } else {
      await createDataSource(currentScmConfigId(), selectedMapping.value.id, payload)
      ElMessage.success('数据源已新增')
    }
    datasourceDialogVisible.value = false
    await loadDataMonitorConfig()
  } catch (error) {
    ElMessage.error(error.message || '保存数据源失败')
  } finally {
    saving.value = false
  }
}

async function toggleDatasource(row) {
  if (!selectedMapping.value || !currentScmConfigId()) return
  try {
    await setDataSourceEnabled(currentScmConfigId(), selectedMapping.value.id, row.id, !row.enabled)
    ElMessage.success('数据源状态已更新')
    await loadDataMonitorConfig()
  } catch (error) {
    ElMessage.error(error.message || '更新数据源状态失败')
  }
}

async function toggleLogTable(row) {
  if (!selectedMapping.value || !currentScmConfigId()) return
  try {
    await setLogTableEnabled(currentScmConfigId(), selectedMapping.value.id, row.id, !row.enabled)
    ElMessage.success('接口日志表状态已更新')
    await loadDataMonitorConfig()
  } catch (error) {
    ElMessage.error(error.message || '更新接口日志表状态失败')
  }
}

function validateDatasourceForm() {
  if (!datasourceForm.datasourceCode) return '请填写数据源编码'
  if (!datasourceForm.jdbcUrl) return '请填写 JDBC URL'
  if (!datasourceForm.username) return '请填写只读用户名'
  if (!editingDatasourceId.value && !datasourceForm.password) return '请填写只读账号密码'
  if (datasourceForm.dbType !== 'mysql') return '当前仅支持 MySQL 数据源'
  if (datasourceForm.dbVersion !== '5.7') return '当前数据库监控设计仅支持 MySQL 5.7'
  if (datasourceForm.readonly !== true) return '数据库监控账号必须保持只读'
  if (Number(datasourceForm.runtimeCollectIntervalSeconds) < 1) return '运行态采集间隔必须大于 0 秒'
  if (Number(datasourceForm.poolMetricPushIntervalSeconds) < 1) return '连接池推送间隔必须大于 0 秒'
  return ''
}

function resetPageForms() {
  Object.assign(monitorForm, createMonitorForm())
  resetDatasourceForm()
  dataSources.value = []
  logTables.value = []
}

function resetDatasourceForm() {
  editingDatasourceId.value = null
  datasourceTestResult.value = null
  Object.assign(datasourceForm, createDatasourceForm())
}

function currentScmConfigId() {
  return selectedScmConfig.value?.id
}

function findScmConfig(mapping) {
  if (!mapping) return null
  return scmConfigs.value.find((item) => normalize(item.scmProvider) === normalize(mapping.scmProvider)
    && Number(item.projectId) === Number(mapping.scmProjectId)) || null
}

function createMonitorForm() {
  return {
    enabled: true,
    ownerTeam: '',
    techOwner: '',
    alertWebhookMode: 'SCM_CONFIG',
    defaultRuntimeCollectIntervalSeconds: 30,
    defaultPoolMetricPushIntervalSeconds: 30,
    defaultLogQualityCheckIntervalSeconds: 300,
    alertScanIntervalSeconds: 60,
    remark: '',
  }
}

function createDatasourceForm() {
  return {
    datasourceCode: '',
    datasourceName: '',
    dbType: 'mysql',
    dbVersion: '5.7',
    jdbcUrl: '',
    databaseName: '',
    username: '',
    password: '',
    readonly: true,
    enabled: true,
    runtimeCollectIntervalSeconds: 30,
    poolMetricPushIntervalSeconds: 30,
    thresholds: {
      longSqlSeconds: 10,
      longTransactionSeconds: 30,
      lockWaitSeconds: 5,
      connectionUsagePercent: 80,
    },
    collectOptions: {
      processlist: true,
      innodbTransaction: true,
      innodbLock: true,
      globalStatus: true,
      explain: true,
      fullSql: true,
    },
  }
}

function composeRepoName(item) {
  if (!item) return ''
  if (item.repoOwner && item.repoName) return `${item.repoOwner}/${item.repoName}`
  return item.projectName || ''
}

function normalize(value) {
  return String(value || '').trim().toLowerCase()
}
</script>

<style scoped>
.monitor-config-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-titlebar,
.section-title,
.tab-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-titlebar h2,
.section-title h3,
.tab-toolbar h3 {
  margin: 4px 0 8px;
}

.page-titlebar p,
.section-title p,
.tab-toolbar p,
.table-sub {
  color: var(--muted);
}

.query-panel,
.panel-card {
  padding: 18px;
}

.tab-toolbar {
  align-items: center;
  margin-bottom: 14px;
}

.table-main {
  font-weight: 700;
  color: var(--text);
}

.table-sub {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.test-result {
  margin-top: 12px;
}
</style>
