<template>
  <el-drawer
    :model-value="visible"
    :title="selectedMapping ? `数据监控配置 - ${selectedMapping.appName}` : '数据监控配置'"
    size="920px"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
  >
    <template v-if="selectedMapping">
      <el-alert
        class="scm-alert"
        type="info"
        show-icon
        :closable="false"
        title="数据监控配置绑定当前 SCM 应用映射，Argus 仅使用只读账号采集，不会在生产库执行索引或 DDL。"
      />

      <div class="form-section">
        <div class="section-title">
          <div>
            <h3>监控总配置</h3>
            <p>控制该 appName 是否接入数据库观测，以及预警沿用 SCM 通知还是自定义策略。</p>
          </div>
          <el-button type="primary" :loading="saving" @click="$emit('save-overview')">保存总配置</el-button>
        </div>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="启用监控">
              <el-switch v-model="monitorForm.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="负责团队">
              <el-input v-model.trim="monitorForm.ownerTeam" placeholder="例如 交易平台" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="技术负责人">
              <el-input v-model.trim="monitorForm.techOwner" placeholder="例如 zhangsan" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="告警 Webhook">
              <el-select v-model="monitorForm.alertWebhookMode" style="width: 100%">
                <el-option label="沿用 SCM Webhook" value="SCM_CONFIG" />
                <el-option label="仅记录不通知" value="NONE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="field-group-title">默认调度频率</div>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="运行态采集秒数">
              <el-input-number v-model="monitorForm.defaultRuntimeCollectIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="连接池推送秒数">
              <el-input-number v-model="monitorForm.defaultPoolMetricPushIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="日志巡检秒数">
              <el-input-number v-model="monitorForm.defaultLogQualityCheckIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="告警扫描秒数">
              <el-input-number v-model="monitorForm.alertScanIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model.trim="monitorForm.remark" type="textarea" :rows="2" placeholder="说明接入范围、库实例或巡检注意事项" />
        </el-form-item>
      </div>

      <div class="form-section">
        <div class="section-title">
          <div>
            <h3>只读数据源</h3>
            <p>用于采集 processlist、事务、锁等待、全局状态与 EXPLAIN；账号必须保持只读。</p>
          </div>
          <div class="section-actions">
            <el-button :loading="datasourceTestLoading" @click="$emit('test-datasource')">测试只读连通性</el-button>
            <el-button v-if="datasourceEditing" @click="$emit('reset-datasource')">取消编辑</el-button>
            <el-button type="primary" :loading="saving" @click="$emit('add-datasource')">
              {{ datasourceEditing ? '保存数据源' : '新增数据源' }}
            </el-button>
          </div>
        </div>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="数据源编码">
              <el-input v-model.trim="datasourceForm.datasourceCode" placeholder="order-main" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="显示名称">
              <el-input v-model.trim="datasourceForm.datasourceName" placeholder="订单主库" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="JDBC URL">
              <el-input v-model.trim="datasourceForm.jdbcUrl" placeholder="jdbc:mysql://host:3306/db?useSSL=false" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="库名">
              <el-input v-model.trim="datasourceForm.databaseName" placeholder="database" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="用户名">
              <el-input v-model.trim="datasourceForm.username" placeholder="readonly_user" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="密码">
              <el-input v-model.trim="datasourceForm.password" type="password" show-password placeholder="只读账号密码" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="启用数据源">
              <el-switch v-model="datasourceForm.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="数据库类型">
              <el-select v-model="datasourceForm.dbType" style="width: 100%">
                <el-option label="MySQL" value="mysql" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="数据库版本">
              <el-select v-model="datasourceForm.dbVersion" style="width: 100%">
                <el-option label="MySQL 5.7" value="5.7" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="只读账号">
              <el-switch v-model="datasourceForm.readonly" active-text="只读" inactive-text="非只读" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="完整 SQL">
              <el-switch v-model="datasourceForm.collectOptions.fullSql" active-text="采集" inactive-text="关闭" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="field-group-title">采集项</div>
        <el-checkbox-group v-model="enabledCollectOptions" class="collect-options">
          <el-checkbox label="processlist">PROCESSLIST</el-checkbox>
          <el-checkbox label="innodbTransaction">INNODB 事务</el-checkbox>
          <el-checkbox label="innodbLock">INNODB 锁等待</el-checkbox>
          <el-checkbox label="globalStatus">GLOBAL STATUS / QPS</el-checkbox>
          <el-checkbox label="explain">EXPLAIN</el-checkbox>
        </el-checkbox-group>
        <div class="field-group-title">采集频率</div>
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
        </el-row>
        <div class="field-group-title">阈值配置</div>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="长 SQL 秒数">
              <el-input-number v-model="datasourceForm.thresholds.longSqlSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="长事务秒数">
              <el-input-number v-model="datasourceForm.thresholds.longTransactionSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="锁等待秒数">
              <el-input-number v-model="datasourceForm.thresholds.lockWaitSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="连接使用率 %">
              <el-input-number v-model="datasourceForm.thresholds.connectionUsagePercent" :min="1" :max="100" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="datasourceTestResult"
          class="test-result"
          :type="datasourceTestResult.connected && datasourceTestResult.readonlyVerified ? 'success' : 'warning'"
          show-icon
          :closable="false"
          :title="datasourceTestResult.message || '只读连通性测试完成'"
        >
          <div class="capability-tags">
            <el-tag :type="datasourceTestResult.connected ? 'success' : 'danger'" effect="plain">连接 {{ yesNo(datasourceTestResult.connected) }}</el-tag>
            <el-tag :type="datasourceTestResult.readonlyVerified ? 'success' : 'danger'" effect="plain">只读 {{ yesNo(datasourceTestResult.readonlyVerified) }}</el-tag>
            <el-tag :type="datasourceTestResult.canExplain ? 'success' : 'warning'" effect="plain">EXPLAIN {{ yesNo(datasourceTestResult.canExplain) }}</el-tag>
            <el-tag :type="datasourceTestResult.canReadProcesslist ? 'success' : 'warning'" effect="plain">PROCESSLIST {{ yesNo(datasourceTestResult.canReadProcesslist) }}</el-tag>
            <el-tag :type="datasourceTestResult.canReadInnodbStatus ? 'success' : 'warning'" effect="plain">INNODB {{ yesNo(datasourceTestResult.canReadInnodbStatus) }}</el-tag>
          </div>
        </el-alert>
        <el-table :data="dataSources" v-loading="loading" border class="mapping-table">
          <el-table-column label="编码" prop="datasourceCode" min-width="150" />
          <el-table-column label="库" min-width="180">
            <template #default="{ row }">
              <div class="table-main">{{ row.databaseName || '-' }}</div>
              <div class="table-sub">{{ row.dbType || 'mysql' }} {{ row.dbVersion || '5.7' }} · {{ row.jdbcUrl || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="账号" prop="username" width="150" />
          <el-table-column label="采集项" min-width="260">
            <template #default="{ row }">
              <el-tag v-if="row.readonly !== false" size="small" type="success" effect="plain">只读</el-tag>
              <el-tag v-if="row.collectProcesslist" size="small" effect="plain">PROCESSLIST</el-tag>
              <el-tag v-if="row.collectInnodbTrx" size="small" effect="plain">事务</el-tag>
              <el-tag v-if="row.collectInnodbLock" size="small" effect="plain">锁等待</el-tag>
              <el-tag v-if="row.collectGlobalStatus" size="small" effect="plain">QPS</el-tag>
              <el-tag v-if="row.explainEnabled" size="small" effect="plain">EXPLAIN</el-tag>
              <el-tag v-if="row.fullSqlCollectEnabled" size="small" effect="plain">完整 SQL</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="阈值" min-width="230">
            <template #default="{ row }">
              <div class="table-sub">长 SQL {{ row.thresholds?.longSqlSeconds ?? '-' }}s；长事务 {{ row.thresholds?.longTransactionSeconds ?? '-' }}s</div>
              <div class="table-sub">锁等待 {{ row.thresholds?.lockWaitSeconds ?? '-' }}s；连接 {{ row.thresholds?.connectionUsagePercent ?? '-' }}%</div>
            </template>
          </el-table-column>
          <el-table-column label="频率" min-width="180">
            <template #default="{ row }">
              <div class="table-sub">运行态 {{ row.runtimeCollectIntervalSeconds ?? '-' }}s</div>
              <div class="table-sub">连接池 {{ row.poolMetricPushIntervalSeconds ?? '-' }}s</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-switch :model-value="Boolean(row.enabled)" @change="$emit('toggle-datasource', row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="$emit('edit-datasource', row)">编辑</el-button>
              <el-button link type="primary" @click="$emit('open-slow-log', row)">配置</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="form-section">
        <div class="section-title">
          <div>
            <h3>接口日志表质量</h3>
            <p>支持不同系统各自配置接口日志表，不限定为 gaea_api_history。</p>
          </div>
          <el-button type="primary" :disabled="!dataSources.length" :loading="saving" @click="$emit('add-log-table')">新增日志表</el-button>
        </div>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="数据源">
              <el-select v-model="logTableForm.datasourceId" placeholder="选择数据源" style="width: 100%">
                <el-option v-for="item in dataSources" :key="item.id" :label="item.datasourceCode" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="配置名称">
              <el-input v-model.trim="logTableForm.configName" placeholder="接口日志表" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="表名">
              <el-input v-model.trim="logTableForm.tableName" placeholder="api_history" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="时间列">
              <el-input v-model.trim="logTableForm.requestTimeColumn" placeholder="request_time" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="主键列">
              <el-input v-model.trim="logTableForm.primaryKeyColumn" placeholder="id" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="接口列">
              <el-input v-model.trim="logTableForm.interfaceCodeColumn" placeholder="interface_code" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="响应列">
              <el-input v-model.trim="logTableForm.responseBodyColumn" placeholder="response_body" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="requestId 列">
              <el-input v-model.trim="logTableForm.requestIdColumn" placeholder="request_id" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="响应时间列">
              <el-input v-model.trim="logTableForm.responseTimeColumn" placeholder="response_time" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态码列">
              <el-input v-model.trim="logTableForm.statusCodeColumn" placeholder="status_code" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="traceId 列">
              <el-input v-model.trim="logTableForm.traceIdColumn" placeholder="trace_id" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="扫描模式">
              <el-select v-model="logTableForm.scanMode" style="width: 100%">
                <el-option label="主键递增" value="ID_INCREMENT" />
                <el-option label="时间窗口" value="TIME_WINDOW" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="巡检间隔秒数">
              <el-input-number v-model="logTableForm.qualityCheckIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="field-group-title">质量规则</div>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="无新数据分钟">
              <el-input-number v-model="logTableForm.qualityRules.noNewDataMinutes" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="空响应阈值 %">
              <el-input-number v-model="logTableForm.qualityRules.emptyRateThreshold" :min="0" :max="100" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="重复阈值 %">
              <el-input-number v-model="logTableForm.qualityRules.duplicateRateThreshold" :min="0" :max="100" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="响应体上限 KB">
              <el-input-number v-model="logTableForm.qualityRules.maxResponseBodyKb" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="必填列">
              <el-select v-model="logTableForm.qualityRules.requiredColumns" multiple filterable allow-create default-first-option style="width: 100%">
                <el-option v-for="item in mappedColumnOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态码白名单">
              <el-select v-model="logTableForm.qualityRules.validStatusCodes" multiple filterable allow-create default-first-option style="width: 100%">
                <el-option label="200" value="200" />
                <el-option label="SUCCESS" value="SUCCESS" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="表行数上限">
              <el-input-number v-model="logTableForm.qualityRules.maxTableRows" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-table :data="logTables" v-loading="loading" border class="mapping-table">
          <el-table-column label="配置" min-width="180">
            <template #default="{ row }">
              <div class="table-main">{{ row.configName || row.tableName }}</div>
              <div class="table-sub">{{ row.tableName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="数据源" prop="datasourceId" width="120" />
          <el-table-column label="关键列" min-width="260">
            <template #default="{ row }">
                <div class="table-sub">PK {{ row.primaryKeyColumn || '-' }}；接口 {{ row.interfaceCodeColumn || '-' }}；时间 {{ row.requestTimeColumn || '-' }}</div>
                <div class="table-sub">响应 {{ row.responseBodyColumn || '-' }}；requestId {{ row.requestIdColumn || '-' }}；traceId {{ row.traceIdColumn || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="巡检频率" width="130">
            <template #default="{ row }">
              {{ row.qualityCheckIntervalSeconds ?? '-' }}s
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-switch :model-value="Boolean(row.enabled)" @change="$emit('toggle-log-table', row)" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </el-drawer>

  <el-dialog
    :model-value="slowLogVisible"
    title="Slow Log 文件接入"
    width="560px"
    @update:model-value="$emit('update:slowLogVisible', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="启用 slow log 文件采集">
        <el-switch v-model="slowLogForm.enabled" active-text="启用" inactive-text="停用" />
      </el-form-item>
      <el-form-item label="文件路径">
        <el-input v-model.trim="slowLogForm.logPath" placeholder="/data/mysql/slow.log" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="字符集">
            <el-input v-model.trim="slowLogForm.charset" placeholder="UTF-8" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="最小耗时 ms">
            <el-input-number v-model="slowLogForm.minQueryTimeMs" :min="0" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="完整 SQL">
        <el-switch v-model="slowLogForm.collectFullSql" active-text="采集" inactive-text="脱敏展示" />
      </el-form-item>
      <el-form-item label="采集间隔秒数">
        <el-input-number v-model="slowLogForm.collectIntervalSeconds" :min="1" controls-position="right" style="width: 100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:slowLogVisible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="$emit('save-slow-log')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  slowLogVisible: {
    type: Boolean,
    default: false,
  },
  selectedMapping: {
    type: Object,
    default: null,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  saving: {
    type: Boolean,
    default: false,
  },
  monitorForm: {
    type: Object,
    required: true,
  },
  datasourceForm: {
    type: Object,
    required: true,
  },
  datasourceTestLoading: {
    type: Boolean,
    default: false,
  },
  datasourceTestResult: {
    type: Object,
    default: null,
  },
  datasourceEditing: {
    type: Boolean,
    default: false,
  },
  dataSources: {
    type: Array,
    default: () => [],
  },
  logTableForm: {
    type: Object,
    required: true,
  },
  logTables: {
    type: Array,
    default: () => [],
  },
  slowLogForm: {
    type: Object,
    required: true,
  },
})

defineEmits([
  'update:visible',
  'update:slowLogVisible',
  'save-overview',
  'test-datasource',
  'add-datasource',
  'edit-datasource',
  'reset-datasource',
  'toggle-datasource',
  'open-slow-log',
  'add-log-table',
  'toggle-log-table',
  'save-slow-log',
])

const mappedColumnOptions = [
  'primaryKeyColumn',
  'interfaceCodeColumn',
  'requestTimeColumn',
  'responseTimeColumn',
  'responseBodyColumn',
  'statusCodeColumn',
  'requestIdColumn',
  'traceIdColumn',
]

const enabledCollectOptions = computed({
  get() {
    const options = props.datasourceForm.collectOptions || {}
    return Object.keys(options).filter((key) => key !== 'fullSql' && options[key])
  },
  set(values) {
    const selected = new Set(values)
    const options = props.datasourceForm.collectOptions || {}
    options.processlist = selected.has('processlist')
    options.innodbTransaction = selected.has('innodbTransaction')
    options.innodbLock = selected.has('innodbLock')
    options.globalStatus = selected.has('globalStatus')
    options.explain = selected.has('explain')
  },
})

function yesNo(value) {
  return value ? '通过' : '未通过'
}
</script>

<style scoped>
.scm-alert {
  margin-bottom: 14px;
}

.form-section {
  margin-bottom: 22px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
}

.section-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.section-actions,
.capability-tags,
.collect-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.collect-options {
  margin-bottom: 14px;
}

.section-title h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.section-title p {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.mapping-table {
  margin-top: 14px;
}

.field-group-title {
  margin: 4px 0 12px;
  color: var(--text);
  font-weight: 700;
}

.test-result {
  margin-bottom: 14px;
}

.table-main {
  display: block;
  font-weight: 700;
  color: var(--text);
  line-height: 1.45;
}

.table-sub {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}
</style>
