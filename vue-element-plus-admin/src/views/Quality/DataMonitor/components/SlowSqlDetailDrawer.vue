<template>
  <el-drawer
    :model-value="modelValue"
    title="慢 SQL 详情"
    size="760px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template v-if="row">
      <div class="detail-block">
        <h4>根因分析</h4>
        <p>{{ row.rootCause || row.analysisSummary || '暂无分析结论' }}</p>
      </div>
      <div class="detail-grid">
        <div
          ><span>应用</span><strong>{{ row.appName || '-' }}</strong></div
        >
        <div
          ><span>数据源</span><strong>{{ row.displayDatasource }}</strong></div
        >
        <div
          ><span>耗时</span><strong>{{ row.displayDuration }}</strong></div
        >
        <div
          ><span>影响行数</span><strong>{{ row.displayRowsExamined }}</strong></div
        >
        <div
          ><span>来源</span><strong>{{ row.displaySourceType }}</strong></div
        >
        <div
          ><span>风险等级</span><strong>{{ row.displayRiskLevel }}</strong></div
        >
        <div
          ><span>根因类型</span><strong>{{ row.displayCauseType }}</strong></div
        >
        <div
          ><span>分析状态</span><strong>{{ row.displayAnalysisStatus }}</strong></div
        >
      </div>
      <div class="detail-block">
        <h4>处理建议</h4>
        <p>{{ row.displayOptimizationSuggestion }}</p>
      </div>
      <div class="detail-block">
        <h4>脱敏 SQL</h4>
        <pre class="code-block">{{ row.displayMaskedSql }}</pre>
      </div>
      <div class="detail-block">
        <h4>完整 SQL 权限</h4>
        <el-alert
          type="info"
          show-icon
          :closable="false"
          title="完整 SQL 已允许采集，但前端按权限展示；无权限时只显示脱敏 SQL。"
        />
        <pre v-if="row.canViewFullSql" class="code-block">{{ row.sqlText || '-' }}</pre>
      </div>
      <div class="detail-block">
        <h4>索引建议</h4>
        <pre class="code-block">{{ row.displayIndexSuggestion }}</pre>
      </div>
      <div class="detail-block">
        <h4>EXPLAIN 结果</h4>
        <el-table v-if="row.hasExplainRows" :data="row.explainRows" border size="small">
          <el-table-column prop="id" label="id" width="70" />
          <el-table-column prop="selectType" label="select_type" min-width="120" />
          <el-table-column prop="tableName" label="table" min-width="120" />
          <el-table-column prop="accessType" label="type" width="100" />
          <el-table-column prop="keyName" label="key" min-width="140" />
          <el-table-column prop="rows" label="rows" width="110" />
          <el-table-column prop="extra" label="Extra" min-width="220" />
        </el-table>
        <el-empty v-else description="暂无 EXPLAIN 结果" />
      </div>
      <div class="detail-block">
        <h4>锁等待现场</h4>
        <div v-if="row.hasLockContext" class="context-grid">
          <div
            ><span>锁事件 ID</span><strong>{{ row.displayRelatedLockId }}</strong></div
          >
          <div
            ><span>锁等待耗时</span><strong>{{ row.displayLockTime }}</strong></div
          >
          <div
            ><span>执行状态</span><strong>{{ row.displayProcessState }}</strong></div
          >
          <div
            ><span>等待秒数</span><strong>{{ row.displayLockWaitSeconds }}</strong></div
          >
          <div
            ><span>锁表</span><strong>{{ row.displayLockTable }}</strong></div
          >
          <div
            ><span>锁索引</span><strong>{{ row.displayLockIndex }}</strong></div
          >
          <div
            ><span>锁类型</span><strong>{{ row.displayLockType }}</strong></div
          >
          <div>
            <span>等待线程 / 阻塞线程</span>
            <strong>{{ row.displayWaitingProcessId }} / {{ row.displayBlockingProcessId }}</strong>
          </div>
        </div>
        <el-empty v-else description="暂无锁等待关联现场" />
      </div>
      <div class="detail-block">
        <h4>连接池关联现场</h4>
        <div v-if="row.hasPoolContext" class="context-grid">
          <div
            ><span>快照 ID</span><strong>{{ row.displayRelatedPoolId }}</strong></div
          >
          <div
            ><span>连接池类型</span><strong>{{ row.displayPoolType }}</strong></div
          >
          <div
            ><span>风险类型</span><strong>{{ row.displayPoolRiskType }}</strong></div
          >
          <div
            ><span>风险等级</span><strong>{{ row.displayPoolRiskLevel }}</strong></div
          >
          <div>
            <span>活跃 / 最大连接</span>
            <strong
              >{{ row.displayPoolActiveConnections }} / {{ row.displayPoolMaxConnections }}</strong
            >
          </div>
          <div
            ><span>等待线程</span><strong>{{ row.displayPoolWaitingThreads }}</strong></div
          >
          <div
            ><span>超时次数</span><strong>{{ row.displayPoolTimeoutCount }}</strong></div
          >
          <div
            ><span>处理角色</span
            ><strong>{{ row.displayNeedDba }}，{{ row.displayNeedDeveloper }}</strong></div
          >
        </div>
        <el-empty v-else description="暂无连接池关联现场" />
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ElAlert, ElDrawer, ElEmpty, ElTable, ElTableColumn } from 'element-plus'

defineProps<{
  modelValue: boolean
  row: Record<string, any> | null
}>()

defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()
</script>

<style scoped>
.detail-block {
  margin-bottom: 18px;
}

.detail-block h4 {
  margin: 0 0 10px;
  font-size: 15px;
}

.detail-block p {
  margin: 0;
  line-height: 1.7;
}

.detail-grid,
.context-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.detail-grid div,
.context-grid div {
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.detail-grid span,
.detail-grid strong,
.context-grid span,
.context-grid strong {
  display: block;
}

.detail-grid span,
.context-grid span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.detail-grid strong,
.context-grid strong {
  margin-top: 6px;
  overflow-wrap: anywhere;
}

.code-block {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
