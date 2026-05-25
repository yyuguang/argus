<template>
  <ContentWrap>
    <div class="monitor-config-page">
      <section class="page-titlebar">
        <div>
          <p class="eyebrow">Data Monitor Config</p>
          <h2>数据监控配置</h2>
          <p>在独立页面中按应用维护数据库监控配置；SCM 只作为关联对象，不承载该模块配置职责。</p>
        </div>
        <el-button :icon="Refresh" :loading="loading" @click="loadOptions">刷新</el-button>
      </section>

      <section class="panel-card query-panel">
        <el-form label-position="top">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="SCM 仓库">
                <el-select
                  v-model="selectedScmConfigId"
                  filterable
                  clearable
                  placeholder="选择 SCM 仓库"
                  style="width: 100%"
                >
                  <el-option
                    v-for="item in scmConfigs"
                    :key="item.id ?? ''"
                    :label="item.projectName || composeRepoName(item) || '-'"
                    :value="item.id ?? ''"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="应用">
                <el-select
                  v-model="selectedMappingId"
                  filterable
                  clearable
                  placeholder="选择 appName"
                  style="width: 100%"
                >
                  <el-option
                    v-for="item in mappingOptions"
                    :key="item.id ?? ''"
                    :label="`${item.appName} · ${item.scmProvider || '-'}:${item.scmProjectId || '-'}`"
                    :value="item.id ?? ''"
                  />
                </el-select>
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

      <el-empty
        v-if="!loading && !scmConfigs.length"
        description="暂无可用 SCM 仓库，请先完成 SCM 配置。"
      />

      <el-empty
        v-else-if="!loading && selectedScmConfig && !mappingOptions.length"
        description="当前仓库下暂无应用联动，请先建立应用联动关系。"
      />

      <el-empty v-else-if="!selectedScmConfig" description="请选择 SCM 仓库后维护数据监控配置" />

      <el-empty v-else-if="!selectedMapping" description="请选择应用后维护数据监控配置" />

      <template v-else>
        <el-row :gutter="16" class="summary-row">
          <el-col :xl="8" :lg="8" :md="8" :sm="24" :xs="24">
            <el-card shadow="never" class="summary-card">
              <p class="summary-label">当前应用</p>
              <h3>{{ selectedMapping?.appName || '-' }}</h3>
              <div class="summary-desc">
                {{ composeRepoName(selectedScmConfig) || selectedScmConfig?.projectName || '-' }}
              </div>
            </el-card>
          </el-col>
          <el-col :xl="8" :lg="8" :md="8" :sm="24" :xs="24">
            <el-card shadow="never" class="summary-card">
              <el-statistic :value="dataSources.length" title="只读数据源" />
              <div class="summary-desc">
                已启用 {{ enabledDataSourceCount }} 个，支持运行态采集与 Slow Log 分析
              </div>
            </el-card>
          </el-col>
          <el-col :xl="8" :lg="8" :md="8" :sm="24" :xs="24">
            <el-card shadow="never" class="summary-card">
              <el-statistic :value="logTables.length" title="接口日志表" />
              <div class="summary-desc">
                已启用 {{ enabledLogTableCount }} 个，支持质量巡检和字段映射校验
              </div>
            </el-card>
          </el-col>
        </el-row>

        <section class="panel-card">
          <div class="section-title">
            <div>
              <h3>监控总配置</h3>
              <p>应用级总开关、负责人、默认频率和告警模式。</p>
            </div>
            <el-button
              type="primary"
              :disabled="!canUpdateConfig"
              :loading="saving"
              @click="saveMonitorOverview"
            >
              保存总配置
            </el-button>
          </div>
          <el-form label-position="top">
            <el-row :gutter="16">
              <el-col :span="4">
                <el-form-item label="启用监控">
                  <el-switch
                    v-model="monitorForm.enabled"
                    active-text="启用"
                    inactive-text="停用"
                  />
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
                  <el-input-number
                    v-model="monitorForm.alertScanIntervalSeconds"
                    :min="1"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="6">
                <el-form-item label="默认运行态采集秒数">
                  <el-input-number
                    v-model="monitorForm.defaultRuntimeCollectIntervalSeconds"
                    :min="1"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="默认连接池推送秒数">
                  <el-input-number
                    v-model="monitorForm.defaultPoolMetricPushIntervalSeconds"
                    :min="1"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="默认日志巡检秒数">
                  <el-input-number
                    v-model="monitorForm.defaultLogQualityCheckIntervalSeconds"
                    :min="1"
                    controls-position="right"
                    style="width: 100%"
                  />
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
                <el-button
                  type="primary"
                  :disabled="!canCreateConfig"
                  @click="openDatasourceDialog()"
                >
                  新增数据源
                </el-button>
              </div>
              <Table
                :columns="datasourceColumns"
                :data="dataSources"
                :loading="configLoading"
                empty-text="当前应用还没有只读数据源配置"
              />
            </el-tab-pane>

            <el-tab-pane label="Slow Log" name="slowLog">
              <div class="tab-toolbar">
                <div>
                  <h3>Slow Log 配置</h3>
                  <p>按数据源维护采集路径、阈值和完整 SQL 采集开关。</p>
                </div>
                <el-button
                  type="primary"
                  :disabled="!canUpdateConfig || !selectedSlowLogDatasourceId"
                  :loading="savingSlowLog"
                  @click="saveSlowLog"
                >
                  保存 Slow Log
                </el-button>
              </div>

              <el-alert
                v-if="!dataSources.length"
                type="info"
                show-icon
                :closable="false"
                title="请先为当前应用配置至少一个只读数据源。"
              />

              <template v-else>
                <el-form label-position="top">
                  <el-row :gutter="16">
                    <el-col :span="8">
                      <el-form-item label="数据源">
                        <el-select v-model="selectedSlowLogDatasourceId" style="width: 100%">
                          <el-option
                            v-for="item in dataSources"
                            :key="item.id ?? ''"
                            :label="`${item.datasourceCode || '-'} · ${item.databaseName || '-'}`"
                            :value="item.id ?? ''"
                          />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="4">
                      <el-form-item label="启用">
                        <el-switch v-model="slowLogForm.enabled" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="6">
                      <el-form-item label="来源类型">
                        <el-select v-model="slowLogForm.sourceType" style="width: 100%">
                          <el-option label="FILE_TAIL" value="FILE_TAIL" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="6">
                      <el-form-item label="字符集">
                        <el-input v-model.trim="slowLogForm.charset" placeholder="UTF-8" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-form-item label="日志路径">
                    <el-input
                      v-model.trim="slowLogForm.logPath"
                      placeholder="/var/log/mysql/slow.log"
                    />
                  </el-form-item>
                  <el-row :gutter="16">
                    <el-col :span="6">
                      <el-form-item label="最小慢 SQL 毫秒数">
                        <el-input-number
                          v-model="slowLogForm.minQueryTimeMs"
                          :min="1"
                          controls-position="right"
                          style="width: 100%"
                        />
                      </el-form-item>
                    </el-col>
                    <el-col :span="6">
                      <el-form-item label="采集间隔秒数">
                        <el-input-number
                          v-model="slowLogForm.collectIntervalSeconds"
                          :min="1"
                          controls-position="right"
                          style="width: 100%"
                        />
                      </el-form-item>
                    </el-col>
                    <el-col :span="6">
                      <el-form-item label="游标偏移">
                        <el-input-number
                          v-model="slowLogForm.cursorOffset"
                          :min="0"
                          controls-position="right"
                          style="width: 100%"
                        />
                      </el-form-item>
                    </el-col>
                    <el-col :span="6">
                      <el-form-item label="完整 SQL">
                        <el-switch v-model="slowLogForm.collectFullSql" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-alert
                    v-if="slowLogLastCollectedAt"
                    type="info"
                    show-icon
                    :closable="false"
                    :title="`最近采集时间：${slowLogLastCollectedAt}`"
                  />
                </el-form>
              </template>
            </el-tab-pane>

            <el-tab-pane label="接口日志表" name="logTable">
              <div class="tab-toolbar">
                <div>
                  <h3>接口日志表</h3>
                  <p>维护字段映射、扫描方式和质量规则，支撑接口日志数据质量巡检。</p>
                </div>
                <el-button
                  type="primary"
                  :disabled="!canCreateConfig || !dataSources.length"
                  @click="openLogTableDialog()"
                >
                  新增日志表
                </el-button>
              </div>
              <el-alert
                v-if="!dataSources.length"
                type="info"
                show-icon
                :closable="false"
                title="请先配置至少一个只读数据源，再维护接口日志表。"
              />
              <el-empty
                v-else-if="!logTables.length"
                description="当前应用还没有接口日志表配置，先新增一条日志表映射。"
              >
                <el-button
                  type="primary"
                  :disabled="!canCreateConfig"
                  @click="openLogTableDialog()"
                >
                  新增日志表
                </el-button>
              </el-empty>
              <Table
                v-else
                :columns="logTableColumns"
                :data="logTables"
                :loading="configLoading"
                empty-text="当前应用还没有接口日志表配置"
              />
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
            <el-input
              v-model.trim="datasourceForm.jdbcUrl"
              placeholder="jdbc:mysql://host:3306/db?useSSL=false"
            />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="用户名" required>
                <el-input v-model.trim="datasourceForm.username" placeholder="readonly_user" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item
                :label="editingDatasourceId ? '密码（留空不修改）' : '密码'"
                :required="!editingDatasourceId"
              >
                <el-input
                  v-model.trim="datasourceForm.password"
                  type="password"
                  show-password
                  placeholder="只读账号密码"
                />
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
                <el-input-number
                  v-model="datasourceForm.runtimeCollectIntervalSeconds"
                  :min="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="连接池推送秒数">
                <el-input-number
                  v-model="datasourceForm.poolMetricPushIntervalSeconds"
                  :min="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="长 SQL 秒数">
                <el-input-number
                  v-model="datasourceForm.thresholds.longSqlSeconds"
                  :min="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="连接使用率 %">
                <el-input-number
                  v-model="datasourceForm.thresholds.connectionUsagePercent"
                  :min="1"
                  :max="100"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="采集能力">
            <el-checkbox v-model="datasourceForm.collectOptions.processlist"
              >PROCESSLIST</el-checkbox
            >
            <el-checkbox v-model="datasourceForm.collectOptions.innodbTransaction"
              >InnoDB 事务</el-checkbox
            >
            <el-checkbox v-model="datasourceForm.collectOptions.innodbLock"
              >InnoDB 锁等待</el-checkbox
            >
            <el-checkbox v-model="datasourceForm.collectOptions.globalStatus"
              >GLOBAL STATUS / QPS</el-checkbox
            >
            <el-checkbox v-model="datasourceForm.collectOptions.explain">EXPLAIN</el-checkbox>
            <el-checkbox v-model="datasourceForm.collectOptions.fullSql">完整 SQL</el-checkbox>
          </el-form-item>
        </el-form>
        <el-alert
          v-if="datasourceTestResult"
          class="test-result"
          :type="
            datasourceTestResult.connected && datasourceTestResult.readonlyVerified
              ? 'success'
              : 'warning'
          "
          show-icon
          :closable="false"
          :title="datasourceTestResult.message || '只读连通性测试完成'"
        />
        <template #footer>
          <el-button @click="datasourceDialogVisible = false">取消</el-button>
          <el-button
            :disabled="!canTestConfig"
            :loading="datasourceTestLoading"
            @click="testCurrentDatasource"
          >
            测试连接
          </el-button>
          <el-button
            type="primary"
            :disabled="!canUpdateConfig"
            :loading="saving"
            @click="saveDatasource"
          >
            保存
          </el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="logTableDialogVisible"
        :title="editingLogTableId ? '编辑接口日志表' : '新增接口日志表'"
        width="960px"
        destroy-on-close
        @closed="resetLogTableForm"
      >
        <el-form label-position="top">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="数据源" required>
                <el-select v-model="logTableForm.datasourceId" style="width: 100%">
                  <el-option
                    v-for="item in dataSources"
                    :key="item.id ?? ''"
                    :label="`${item.datasourceCode || '-'} · ${item.databaseName || '-'}`"
                    :value="item.id ?? ''"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="配置名称" required>
                <el-input v-model.trim="logTableForm.configName" placeholder="订单接口日志" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="表名" required>
                <el-input v-model.trim="logTableForm.tableName" placeholder="gaea_api_history" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="主键列" required>
                <el-input v-model.trim="logTableForm.primaryKeyColumn" placeholder="id" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="接口编码列" required>
                <el-input
                  v-model.trim="logTableForm.interfaceCodeColumn"
                  placeholder="interface_code"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="扫描模式">
                <el-select v-model="logTableForm.scanMode" style="width: 100%">
                  <el-option label="ID_INCREMENT" value="ID_INCREMENT" />
                  <el-option label="TIME_WINDOW" value="TIME_WINDOW" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="请求时间列" required>
                <el-input v-model.trim="logTableForm.requestTimeColumn" placeholder="create_time" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="响应时间列" required>
                <el-input
                  v-model.trim="logTableForm.responseTimeColumn"
                  placeholder="response_time"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="状态码列">
                <el-input v-model.trim="logTableForm.statusCodeColumn" placeholder="status_code" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="请求 ID 列">
                <el-input v-model.trim="logTableForm.requestIdColumn" placeholder="request_id" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="链路 ID 列">
                <el-input v-model.trim="logTableForm.traceIdColumn" placeholder="trace_id" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="响应体列">
                <el-input
                  v-model.trim="logTableForm.responseBodyColumn"
                  placeholder="response_body"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="巡检间隔秒数">
                <el-input-number
                  v-model="logTableForm.qualityCheckIntervalSeconds"
                  :min="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="启用">
                <el-switch v-model="logTableForm.enabled" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">质量规则</el-divider>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="无数据阈值（分钟）">
                <el-input-number
                  v-model="logTableForm.qualityRules.noDataMinutes"
                  :min="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="空响应阈值（%）">
                <el-input-number
                  v-model="logTableForm.qualityRules.emptyResponseThreshold"
                  :min="0"
                  :max="100"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="重复阈值（%）">
                <el-input-number
                  v-model="logTableForm.qualityRules.duplicateThreshold"
                  :min="0"
                  :max="100"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="响应体长度上限">
                <el-input-number
                  v-model="logTableForm.qualityRules.responseBodyMaxLength"
                  :min="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="扫描行数上限">
                <el-input-number
                  v-model="logTableForm.qualityRules.rowCountUpperBound"
                  :min="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="必填字段（逗号分隔）">
                <el-input
                  v-model.trim="logTableForm.requiredColumnsText"
                  placeholder="id,interface_code,create_time,response_time,response_body,status_code,request_id,trace_id"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="成功状态码（逗号分隔）">
                <el-input v-model.trim="logTableForm.successStatusCodesText" placeholder="200,0" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <template #footer>
          <el-button @click="logTableDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :disabled="!canUpdateConfig"
            :loading="savingLogTable"
            @click="saveLogTable"
          >
            保存
          </el-button>
        </template>
      </el-dialog>

      <el-drawer
        v-model="logTableDetailVisible"
        title="接口日志表详情"
        size="720px"
        destroy-on-close
      >
        <el-descriptions v-if="logTableDetail" :column="2" border class="log-table-detail">
          <el-descriptions-item label="配置名称">
            {{ logTableDetail.configName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="所属数据源">
            {{ datasourceDisplayName(logTableDetail.datasourceId) }}
          </el-descriptions-item>
          <el-descriptions-item label="表名">
            {{ logTableDetail.tableName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="扫描模式">
            {{ logTableDetail.scanMode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="主键列">
            {{ logTableDetail.primaryKeyColumn || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="接口编码列">
            {{ logTableDetail.interfaceCodeColumn || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="请求时间列">
            {{ logTableDetail.requestTimeColumn || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="响应时间列">
            {{ logTableDetail.responseTimeColumn || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="请求 ID 列">
            {{ logTableDetail.requestIdColumn || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="链路 ID 列">
            {{ logTableDetail.traceIdColumn || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态码列">
            {{ logTableDetail.statusCodeColumn || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="响应体列">
            {{ logTableDetail.responseBodyColumn || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="巡检间隔秒数">
            {{ logTableDetail.qualityCheckIntervalSeconds ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="启用状态">
            <el-tag :type="logTableDetail.enabled !== false ? 'success' : 'info'">
              {{ logTableDetail.enabled !== false ? '启用' : '停用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="必填字段" :span="2">
            {{ (logTableDetail.qualityRules?.requiredColumns || []).join(', ') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="成功状态码" :span="2">
            {{ (logTableDetail.qualityRules?.successStatusCodes || []).join(', ') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="质量规则" :span="2">
            无数据 {{ logTableDetail.qualityRules?.noDataMinutes ?? '-' }} 分钟，空响应
            {{ logTableDetail.qualityRules?.emptyResponseThreshold ?? '-' }}%，重复
            {{ logTableDetail.qualityRules?.duplicateThreshold ?? '-' }}%，响应体上限
            {{ logTableDetail.qualityRules?.responseBodyMaxLength ?? '-' }}，扫描行数上限
            {{ logTableDetail.qualityRules?.rowCountUpperBound ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-drawer>
    </div>
  </ContentWrap>
</template>

<script setup lang="tsx">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDivider,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRow,
  ElSelect,
  ElStatistic,
  ElSwitch,
  ElTabPane,
  ElTabs,
  ElTag
} from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { ContentWrap } from '@/components/ContentWrap'
import { BaseButton } from '@/components/Button'
import { hasPermi } from '@/components/Permission/src/utils'
import { Table, TableColumn } from '@/components/Table'
import { getProjectMappingsApi, listScmConfigsApi } from '@/api/scm'
import type { ProjectMappingItem, ScmConfigItem } from '@/api/scm/types'
import {
  createLogTableApi,
  createDataSourceApi,
  deleteLogTableApi,
  fetchDataMonitorOverviewApi,
  fetchDataSourcesApi,
  fetchLogTablesApi,
  fetchSlowLogConfigApi,
  saveDataMonitorOverviewApi,
  saveSlowLogConfigApi,
  setDataSourceEnabledApi,
  setLogTableEnabledApi,
  testDataSourceApi,
  testExistingDataSourceApi,
  updateLogTableApi,
  updateDataSourceApi
} from '@/api/dataMonitor'
import type {
  DataMonitorConfigOverview,
  DataSourceConfigItem,
  DataSourceTestResult,
  LogTableConfigPayload,
  LogTableConfigItem,
  SlowLogConfigItem
} from '@/api/dataMonitor/types'

const route = useRoute()
const loading = ref(false)
const configLoading = ref(false)
const saving = ref(false)
const savingSlowLog = ref(false)
const scmConfigs = ref<ScmConfigItem[]>([])
const projectMappings = ref<ProjectMappingItem[]>([])
const selectedScmConfigId = ref<string | number | ''>('')
const selectedMappingId = ref<string | number | ''>('')
const activeTab = ref('datasource')
const datasourceDialogVisible = ref(false)
const datasourceTestLoading = ref(false)
const datasourceTestResult = ref<DataSourceTestResult | null>(null)
const testingDatasourceId = ref<string | number | null>(null)
const dataSources = ref<DataSourceConfigItem[]>([])
const logTables = ref<LogTableConfigItem[]>([])
const editingDatasourceId = ref<string | number | null>(null)
const selectedSlowLogDatasourceId = ref<string | number | ''>('')
const slowLogLastCollectedAt = ref('')
const logTableDialogVisible = ref(false)
const savingLogTable = ref(false)
const editingLogTableId = ref<string | number | null>(null)
const logTableDetailVisible = ref(false)
const logTableDetail = ref<LogTableConfigItem | null>(null)

const monitorForm = reactive(createMonitorForm())
const datasourceForm = reactive(createDatasourceForm())
const slowLogForm = reactive(createSlowLogForm())
const logTableForm = reactive(createLogTableForm())

const selectedScmConfig = computed(
  () => scmConfigs.value.find((item) => item.id === selectedScmConfigId.value) || null
)
const mappingOptions = computed(() => {
  const currentScmConfig = selectedScmConfig.value
  if (!currentScmConfig) return []
  return projectMappings.value.filter((item) => matchesScmConfig(item, currentScmConfig))
})
const selectedMapping = computed(
  () => mappingOptions.value.find((item) => item.id === selectedMappingId.value) || null
)
const enabledDataSourceCount = computed(
  () => dataSources.value.filter((item) => item.enabled !== false).length
)
const enabledLogTableCount = computed(
  () => logTables.value.filter((item) => item.enabled !== false).length
)

const canCreateConfig = computed(() => hasPermi('create'))
const canUpdateConfig = computed(() => hasPermi('update'))
const canDeleteConfig = computed(() => hasPermi('delete'))
const canTestConfig = computed(() => hasPermi('test'))

const datasourceColumns = computed<TableColumn[]>(() => [
  {
    field: 'datasourceCode',
    label: '编码',
    minWidth: 140
  },
  {
    field: 'databaseInfo',
    label: '库',
    minWidth: 240,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="table-main">{row.databaseName || '-'}</div>
          <div class="table-sub">{row.jdbcUrl || '-'}</div>
        </div>
      )
    }
  },
  {
    field: 'username',
    label: '账号',
    width: 150
  },
  {
    field: 'collectInterval',
    label: '频率',
    width: 170,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="table-sub">运行态 {row.runtimeCollectIntervalSeconds ?? '-'}s</div>
          <div class="table-sub">连接池 {row.poolMetricPushIntervalSeconds ?? '-'}s</div>
        </div>
      )
    }
  },
  {
    field: 'capabilities',
    label: '能力',
    minWidth: 220,
    slots: {
      default: ({ row }) => (
        <div class="flex flex-wrap gap-6px">
          {row.readonly !== false ? (
            <ElTag size="small" type="success" effect="plain">
              只读
            </ElTag>
          ) : null}
          {row.collectProcesslist ? (
            <ElTag size="small" effect="plain">
              PROCESSLIST
            </ElTag>
          ) : null}
          {row.collectGlobalStatus ? (
            <ElTag size="small" effect="plain">
              QPS
            </ElTag>
          ) : null}
          {row.explainEnabled ? (
            <ElTag size="small" effect="plain">
              EXPLAIN
            </ElTag>
          ) : null}
          {row.fullSqlCollectEnabled ? (
            <ElTag size="small" effect="plain">
              完整 SQL
            </ElTag>
          ) : null}
        </div>
      )
    }
  },
  {
    field: 'enabled',
    label: '状态',
    width: 120,
    slots: {
      default: ({ row }) => (
        <ElSwitch
          modelValue={Boolean(row.enabled)}
          disabled={!canUpdateConfig.value}
          onChange={() => toggleDatasource(row)}
        />
      )
    }
  },
  {
    field: 'action',
    label: '操作',
    width: 220,
    fixed: 'right',
    slots: {
      default: ({ row }) => (
        <div class="flex flex-wrap gap-8px">
          <BaseButton
            type="primary"
            disabled={!canUpdateConfig.value}
            onClick={() => openDatasourceDialog(row)}
          >
            编辑
          </BaseButton>
          <BaseButton
            disabled={!canTestConfig.value}
            loading={testingDatasourceId.value === row.id}
            onClick={() => testSavedDatasource(row)}
          >
            测试连接
          </BaseButton>
        </div>
      )
    }
  }
])

const logTableColumns = computed<TableColumn[]>(() => [
  {
    field: 'datasourceName',
    label: '数据源',
    minWidth: 180,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="table-main">{datasourceDisplayName(row.datasourceId)}</div>
          <div class="table-sub">库 {datasourceDbName(row.datasourceId)}</div>
        </div>
      )
    }
  },
  {
    field: 'configName',
    label: '配置名称',
    minWidth: 160
  },
  {
    field: 'tableName',
    label: '表名',
    minWidth: 160
  },
  {
    field: 'scanMode',
    label: '扫描模式',
    width: 140
  },
  {
    field: 'qualityCheckIntervalSeconds',
    label: '巡检秒数',
    width: 120
  },
  {
    field: 'fieldSummary',
    label: '字段摘要',
    minWidth: 220,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="table-main">
            {row.requestTimeColumn || '-'} / {row.responseTimeColumn || '-'}
          </div>
          <div class="table-sub">
            traceId {row.traceIdColumn || '-'} · requestId {row.requestIdColumn || '-'}
          </div>
        </div>
      )
    }
  },
  {
    field: 'qualityRulesSummary',
    label: '质量规则',
    minWidth: 260,
    slots: {
      default: ({ row }) => (
        <div>
          <div class="table-sub">无数据 {row.qualityRules?.noDataMinutes ?? '-'} 分钟</div>
          <div class="table-sub">
            空响应 {row.qualityRules?.emptyResponseThreshold ?? '-'}% · 重复{' '}
            {row.qualityRules?.duplicateThreshold ?? '-'}%
          </div>
        </div>
      )
    }
  },
  {
    field: 'enabled',
    label: '状态',
    width: 120,
    slots: {
      default: ({ row }) => (
        <ElSwitch
          modelValue={Boolean(row.enabled)}
          disabled={!canUpdateConfig.value}
          onChange={() => toggleLogTable(row)}
        />
      )
    }
  },
  {
    field: 'action',
    label: '操作',
    width: 220,
    fixed: 'right',
    slots: {
      default: ({ row }) => (
        <div class="flex flex-wrap gap-8px">
          <BaseButton onClick={() => openLogTableDetail(row)}>详情</BaseButton>
          <BaseButton
            type="primary"
            disabled={!canUpdateConfig.value}
            onClick={() => openLogTableDialog(row)}
          >
            编辑
          </BaseButton>
          {canDeleteConfig.value ? (
            <BaseButton type="danger" onClick={() => removeLogTable(row)}>
              删除
            </BaseButton>
          ) : null}
        </div>
      )
    }
  }
])

watch(selectedScmConfigId, () => {
  resetPageForms()
  if (!selectedScmConfig.value) {
    selectedMappingId.value = ''
    return
  }
  const currentMapping = selectedMapping.value
  if (currentMapping && matchesScmConfig(currentMapping, selectedScmConfig.value)) {
    return
  }
  selectedMappingId.value = (mappingOptions.value[0]?.id as string | number | undefined) || ''
})

watch(selectedMappingId, async () => {
  resetPageForms()
  if (selectedScmConfig.value && selectedMapping.value) {
    await loadDataMonitorConfig()
  }
})

watch(selectedSlowLogDatasourceId, async (value) => {
  if (value) {
    await loadSlowLogConfig(value)
  } else {
    resetSlowLogForm()
  }
})

onMounted(loadOptions)

function getSelection() {
  const scmConfigId = currentScmConfigId()
  const mappingId = selectedMapping.value?.id
  if (!scmConfigId || mappingId === undefined || mappingId === null || mappingId === '') {
    return null
  }
  return {
    scmConfigId,
    mappingId: mappingId as string | number
  }
}

async function loadOptions() {
  loading.value = true
  try {
    const [configsRes, mappingsRes] = await Promise.all([
      listScmConfigsApi(),
      getProjectMappingsApi()
    ])
    scmConfigs.value = configsRes.data || []
    projectMappings.value = mappingsRes.data || []
    const queryScmConfigId = Number(route.query.scmConfigId || 0)
    const queryMappingId = Number(route.query.mappingId || 0)
    const nextMapping =
      projectMappings.value.find((item) => Number(item.id) === queryMappingId) || null
    const nextScmConfig =
      (nextMapping ? findScmConfig(nextMapping) : null) ||
      scmConfigs.value.find((item) => Number(item.id) === queryScmConfigId) ||
      scmConfigs.value[0] ||
      null

    selectedScmConfigId.value = (nextScmConfig?.id as string | number | undefined) || ''
    const filteredMappings = nextScmConfig
      ? projectMappings.value.filter((item) => matchesScmConfig(item, nextScmConfig))
      : []
    const initialMapping =
      filteredMappings.find((item) => Number(item.id) === queryMappingId) || filteredMappings[0]
    selectedMappingId.value = (initialMapping?.id as string | number | undefined) || ''
  } catch (error: any) {
    ElMessage.error(error?.message || '加载数据监控配置失败')
  } finally {
    loading.value = false
  }
}

async function loadDataMonitorConfig() {
  const selection = getSelection()
  if (!selection) return
  configLoading.value = true
  try {
    const [overviewRes, datasourceRes, logTableRes] = await Promise.all([
      fetchDataMonitorOverviewApi(selection.scmConfigId, selection.mappingId),
      fetchDataSourcesApi(selection.scmConfigId, selection.mappingId),
      fetchLogTablesApi(selection.scmConfigId, selection.mappingId)
    ])
    const overview = (overviewRes.data || {}) as DataMonitorConfigOverview
    Object.assign(monitorForm, {
      enabled: overview.enabled !== false,
      ownerTeam: overview.ownerTeam || '',
      techOwner: overview.techOwner || '',
      alertWebhookMode: overview.alertWebhookMode || 'SCM_CONFIG',
      defaultRuntimeCollectIntervalSeconds: overview.defaultRuntimeCollectIntervalSeconds ?? 30,
      defaultPoolMetricPushIntervalSeconds: overview.defaultPoolMetricPushIntervalSeconds ?? 30,
      defaultLogQualityCheckIntervalSeconds: overview.defaultLogQualityCheckIntervalSeconds ?? 300,
      alertScanIntervalSeconds: overview.alertScanIntervalSeconds ?? 60,
      remark: overview.remark || ''
    })
    dataSources.value = datasourceRes.data || []
    logTables.value = logTableRes.data || []
    selectedSlowLogDatasourceId.value =
      (dataSources.value[0]?.id as string | number | undefined) || ''
  } catch (error: any) {
    ElMessage.error(error?.message || '加载应用监控配置失败')
  } finally {
    configLoading.value = false
  }
}

async function loadSlowLogConfig(datasourceId: string | number) {
  const selection = getSelection()
  if (!selection) return
  try {
    const res = await fetchSlowLogConfigApi(
      selection.scmConfigId,
      selection.mappingId,
      datasourceId
    )
    const config = (res.data || {}) as SlowLogConfigItem
    Object.assign(slowLogForm, {
      enabled: config.enabled !== false,
      sourceType: config.sourceType || 'FILE_TAIL',
      logPath: config.logPath || '',
      charset: config.charset || 'UTF-8',
      minQueryTimeMs: config.minQueryTimeMs ?? 1000,
      collectFullSql: config.collectFullSql === true,
      collectIntervalSeconds: config.collectIntervalSeconds ?? 30,
      cursorOffset: config.cursorOffset ?? 0
    })
    slowLogLastCollectedAt.value = config.lastCollectedAt || ''
  } catch {
    resetSlowLogForm()
  }
}

async function saveMonitorOverview() {
  const selection = getSelection()
  if (!selection) return
  saving.value = true
  try {
    await saveDataMonitorOverviewApi(selection.scmConfigId, selection.mappingId, {
      enabled: monitorForm.enabled,
      ownerTeam: monitorForm.ownerTeam || null,
      techOwner: monitorForm.techOwner || null,
      alertWebhookMode: monitorForm.alertWebhookMode || 'SCM_CONFIG',
      defaultRuntimeCollectIntervalSeconds: monitorForm.defaultRuntimeCollectIntervalSeconds,
      defaultPoolMetricPushIntervalSeconds: monitorForm.defaultPoolMetricPushIntervalSeconds,
      defaultLogQualityCheckIntervalSeconds: monitorForm.defaultLogQualityCheckIntervalSeconds,
      alertScanIntervalSeconds: monitorForm.alertScanIntervalSeconds,
      remark: monitorForm.remark || null
    })
    ElMessage.success('监控总配置已保存')
    await loadDataMonitorConfig()
  } catch (error: any) {
    ElMessage.error(error?.message || '保存监控总配置失败')
  } finally {
    saving.value = false
  }
}

async function saveSlowLog() {
  const selection = getSelection()
  if (!selection || !selectedSlowLogDatasourceId.value) return
  if (!slowLogForm.logPath) {
    ElMessage.warning('请填写 slow log 日志路径')
    return
  }
  savingSlowLog.value = true
  try {
    await saveSlowLogConfigApi(
      selection.scmConfigId,
      selection.mappingId,
      selectedSlowLogDatasourceId.value,
      {
        enabled: slowLogForm.enabled,
        sourceType: slowLogForm.sourceType,
        logPath: slowLogForm.logPath,
        charset: slowLogForm.charset,
        minQueryTimeMs: slowLogForm.minQueryTimeMs,
        collectFullSql: slowLogForm.collectFullSql,
        collectIntervalSeconds: slowLogForm.collectIntervalSeconds,
        cursorOffset: slowLogForm.cursorOffset
      }
    )
    ElMessage.success('Slow Log 配置已保存')
    await loadSlowLogConfig(selectedSlowLogDatasourceId.value)
  } catch (error: any) {
    ElMessage.error(error?.message || '保存 Slow Log 配置失败')
  } finally {
    savingSlowLog.value = false
  }
}

function openDatasourceDialog(row?: DataSourceConfigItem) {
  datasourceTestResult.value = null
  if (row) {
    editingDatasourceId.value = row.id || null
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
        connectionUsagePercent: row.thresholds?.connectionUsagePercent ?? 80
      },
      collectOptions: {
        processlist: row.collectProcesslist !== false,
        innodbTransaction: row.collectInnodbTrx !== false,
        innodbLock: row.collectInnodbLock !== false,
        globalStatus: row.collectGlobalStatus !== false,
        explain: row.explainEnabled !== false,
        fullSql: row.fullSqlCollectEnabled !== false
      }
    })
  } else {
    resetDatasourceForm()
  }
  datasourceDialogVisible.value = true
}

async function testCurrentDatasource() {
  const selection = getSelection()
  if (!selection) return
  datasourceTestLoading.value = true
  datasourceTestResult.value = null
  try {
    if (editingDatasourceId.value) {
      if (!datasourceForm.jdbcUrl || !datasourceForm.username) {
        ElMessage.warning('请填写 JDBC URL 和只读用户名后再测试')
        return
      }
      const res = await testExistingDataSourceApi(
        selection.scmConfigId,
        selection.mappingId,
        editingDatasourceId.value,
        {
          jdbcUrl: datasourceForm.jdbcUrl,
          username: datasourceForm.username,
          password: datasourceForm.password || null
        }
      )
      datasourceTestResult.value = res.data || null
    } else {
      if (!datasourceForm.jdbcUrl || !datasourceForm.username || !datasourceForm.password) {
        ElMessage.warning('请填写 JDBC URL、只读用户名和密码后再测试')
        return
      }
      const res = await testDataSourceApi(selection.scmConfigId, selection.mappingId, {
        jdbcUrl: datasourceForm.jdbcUrl,
        username: datasourceForm.username,
        password: datasourceForm.password
      })
      datasourceTestResult.value = res.data || null
    }
    ElMessage.success('连接测试完成')
  } catch (error: any) {
    datasourceTestResult.value = {
      connected: false,
      readonlyVerified: false,
      canExplain: false,
      canReadProcesslist: false,
      canReadInnodbStatus: false,
      message: error?.message || '连接测试失败'
    }
    ElMessage.error(error?.message || '连接测试失败')
  } finally {
    datasourceTestLoading.value = false
  }
}

async function testSavedDatasource(row: DataSourceConfigItem) {
  const selection = getSelection()
  if (!selection || !row.id) return
  testingDatasourceId.value = row.id
  try {
    const res = await testExistingDataSourceApi(selection.scmConfigId, selection.mappingId, row.id)
    const result = res.data || {}
    if (result.connected && result.readonlyVerified) {
      ElMessage.success(result.message || '数据源连接测试通过')
    } else {
      ElMessage.warning(result.message || '数据源连接测试未完全通过')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '数据源连接测试失败')
  } finally {
    testingDatasourceId.value = null
  }
}

async function saveDatasource() {
  const selection = getSelection()
  if (!selection) return
  const validationMessage = validateDatasourceForm()
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }
  saving.value = true
  try {
    const payload = {
      ...datasourceForm,
      datasourceName: datasourceForm.datasourceName || datasourceForm.datasourceCode
    }
    if (editingDatasourceId.value) {
      await updateDataSourceApi(
        selection.scmConfigId,
        selection.mappingId,
        editingDatasourceId.value,
        payload
      )
      ElMessage.success('数据源已保存')
    } else {
      await createDataSourceApi(selection.scmConfigId, selection.mappingId, payload)
      ElMessage.success('数据源已新增')
    }
    datasourceDialogVisible.value = false
    await loadDataMonitorConfig()
  } catch (error: any) {
    ElMessage.error(error?.message || '保存数据源失败')
  } finally {
    saving.value = false
  }
}

async function toggleDatasource(row: DataSourceConfigItem) {
  const selection = getSelection()
  if (!selection || !row.id) return
  try {
    await setDataSourceEnabledApi(selection.scmConfigId, selection.mappingId, row.id, !row.enabled)
    ElMessage.success('数据源状态已更新')
    await loadDataMonitorConfig()
  } catch (error: any) {
    ElMessage.error(error?.message || '更新数据源状态失败')
  }
}

async function toggleLogTable(row: LogTableConfigItem) {
  const selection = getSelection()
  if (!selection || !row.id) return
  try {
    await setLogTableEnabledApi(selection.scmConfigId, selection.mappingId, row.id, !row.enabled)
    ElMessage.success('接口日志表状态已更新')
    await loadDataMonitorConfig()
  } catch (error: any) {
    ElMessage.error(error?.message || '更新接口日志表状态失败')
  }
}

function openLogTableDetail(row: LogTableConfigItem) {
  logTableDetail.value = row
  logTableDetailVisible.value = true
}

function openLogTableDialog(row?: LogTableConfigItem) {
  if (row) {
    editingLogTableId.value = row.id || null
    Object.assign(logTableForm, {
      datasourceId: row.datasourceId || '',
      configName: row.configName || '',
      tableName: row.tableName || '',
      primaryKeyColumn: row.primaryKeyColumn || '',
      interfaceCodeColumn: row.interfaceCodeColumn || '',
      requestTimeColumn: row.requestTimeColumn || '',
      responseTimeColumn: row.responseTimeColumn || '',
      responseBodyColumn: row.responseBodyColumn || '',
      statusCodeColumn: row.statusCodeColumn || '',
      requestIdColumn: row.requestIdColumn || '',
      traceIdColumn: row.traceIdColumn || '',
      scanMode: row.scanMode || 'ID_INCREMENT',
      qualityCheckIntervalSeconds: row.qualityCheckIntervalSeconds ?? 300,
      enabled: row.enabled !== false,
      qualityRules: {
        noDataMinutes: row.qualityRules?.noDataMinutes ?? 15,
        emptyResponseThreshold: row.qualityRules?.emptyResponseThreshold ?? 20,
        duplicateThreshold: row.qualityRules?.duplicateThreshold ?? 20,
        responseBodyMaxLength: row.qualityRules?.responseBodyMaxLength ?? 65535,
        rowCountUpperBound: row.qualityRules?.rowCountUpperBound ?? 100000
      },
      requiredColumnsText: (row.qualityRules?.requiredColumns || []).join(','),
      successStatusCodesText: (row.qualityRules?.successStatusCodes || []).join(',')
    })
  } else {
    resetLogTableForm()
  }
  logTableDialogVisible.value = true
}

async function saveLogTable() {
  const selection = getSelection()
  if (!selection) return
  const validationMessage = validateLogTableForm()
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }
  savingLogTable.value = true
  try {
    const payload = buildLogTablePayload()
    if (editingLogTableId.value) {
      await updateLogTableApi(
        selection.scmConfigId,
        selection.mappingId,
        editingLogTableId.value,
        payload
      )
      ElMessage.success('接口日志表已保存')
    } else {
      await createLogTableApi(selection.scmConfigId, selection.mappingId, payload)
      ElMessage.success('接口日志表已新增')
    }
    logTableDialogVisible.value = false
    await loadDataMonitorConfig()
  } catch (error: any) {
    ElMessage.error(error?.message || '保存接口日志表失败')
  } finally {
    savingLogTable.value = false
  }
}

async function removeLogTable(row: LogTableConfigItem) {
  const selection = getSelection()
  if (!selection || !row.id) return
  await ElMessageBox.confirm(
    `删除后，接口日志表「${row.configName || row.tableName || row.id}」将停止质量巡检和字段映射校验。确认删除吗？`,
    '删除接口日志表',
    { type: 'warning' }
  )
  try {
    await deleteLogTableApi(selection.scmConfigId, selection.mappingId, row.id)
    ElMessage.success('接口日志表已删除')
    await loadDataMonitorConfig()
  } catch (error: any) {
    ElMessage.error(error?.message || '删除接口日志表失败')
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
  resetSlowLogForm()
  resetLogTableForm()
  dataSources.value = []
  logTables.value = []
  slowLogLastCollectedAt.value = ''
}

function resetDatasourceForm() {
  editingDatasourceId.value = null
  datasourceTestResult.value = null
  Object.assign(datasourceForm, createDatasourceForm())
}

function resetSlowLogForm() {
  Object.assign(slowLogForm, createSlowLogForm())
  slowLogLastCollectedAt.value = ''
}

function resetLogTableForm() {
  editingLogTableId.value = null
  Object.assign(logTableForm, createLogTableForm())
}

function currentScmConfigId() {
  return (selectedScmConfig.value?.id as string | number | undefined) || null
}

function findScmConfig(mapping?: ProjectMappingItem | null) {
  if (!mapping) return null
  return scmConfigs.value.find((item) => matchesScmConfig(mapping, item)) || null
}

function matchesScmConfig(mapping: ProjectMappingItem, scmConfig?: ScmConfigItem | null) {
  if (!scmConfig) return false
  return (
    normalize(scmConfig.scmProvider) === normalize(mapping.scmProvider) &&
    Number(scmConfig.projectId) === Number(mapping.scmProjectId)
  )
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
    remark: ''
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
      connectionUsagePercent: 80
    },
    collectOptions: {
      processlist: true,
      innodbTransaction: true,
      innodbLock: true,
      globalStatus: true,
      explain: true,
      fullSql: true
    }
  }
}

function createSlowLogForm() {
  return {
    enabled: true,
    sourceType: 'FILE_TAIL',
    logPath: '',
    charset: 'UTF-8',
    minQueryTimeMs: 1000,
    collectFullSql: false,
    collectIntervalSeconds: 30,
    cursorOffset: 0
  }
}

function createLogTableForm() {
  return {
    datasourceId: '',
    configName: '',
    tableName: '',
    primaryKeyColumn: 'id',
    interfaceCodeColumn: 'interface_code',
    requestTimeColumn: 'create_time',
    responseTimeColumn: 'response_time',
    responseBodyColumn: 'response_body',
    statusCodeColumn: 'status_code',
    requestIdColumn: 'request_id',
    traceIdColumn: 'trace_id',
    scanMode: 'ID_INCREMENT',
    qualityCheckIntervalSeconds: 300,
    enabled: true,
    qualityRules: {
      noDataMinutes: 15,
      emptyResponseThreshold: 20,
      duplicateThreshold: 20,
      responseBodyMaxLength: 65535,
      rowCountUpperBound: 100000
    },
    requiredColumnsText:
      'id,interface_code,create_time,response_time,response_body,status_code,request_id,trace_id',
    successStatusCodesText: '200,0'
  }
}

function composeRepoName(item?: ScmConfigItem | null) {
  if (!item) return ''
  if (item.repoOwner && item.repoName) return `${item.repoOwner}/${item.repoName}`
  return item.projectName || ''
}

function normalize(value: unknown) {
  return String(value || '')
    .trim()
    .toLowerCase()
}

function datasourceDisplayName(datasourceId?: string | number) {
  const datasource = dataSources.value.find((item) => item.id === datasourceId)
  if (!datasource) return '-'
  return datasource.datasourceCode || datasource.datasourceName || '-'
}

function datasourceDbName(datasourceId?: string | number) {
  const datasource = dataSources.value.find((item) => item.id === datasourceId)
  if (!datasource) return '-'
  return datasource.databaseName || '-'
}

function validateLogTableForm() {
  if (!logTableForm.datasourceId) return '请选择所属数据源'
  if (!logTableForm.configName) return '请填写配置名称'
  if (!logTableForm.tableName) return '请填写表名'
  if (!logTableForm.primaryKeyColumn) return '请填写主键列'
  if (!logTableForm.interfaceCodeColumn) return '请填写接口编码列'
  if (!logTableForm.requestTimeColumn) return '请填写请求时间列'
  if (!logTableForm.responseTimeColumn) return '请填写响应时间列'
  if (Number(logTableForm.qualityCheckIntervalSeconds) < 1) return '巡检间隔必须大于 0 秒'
  return ''
}

function buildLogTablePayload(): LogTableConfigPayload {
  return {
    datasourceId: logTableForm.datasourceId,
    configName: logTableForm.configName || null,
    tableName: logTableForm.tableName || null,
    primaryKeyColumn: logTableForm.primaryKeyColumn || null,
    interfaceCodeColumn: logTableForm.interfaceCodeColumn || null,
    requestTimeColumn: logTableForm.requestTimeColumn || null,
    responseTimeColumn: logTableForm.responseTimeColumn || null,
    responseBodyColumn: logTableForm.responseBodyColumn || null,
    statusCodeColumn: logTableForm.statusCodeColumn || null,
    requestIdColumn: logTableForm.requestIdColumn || null,
    traceIdColumn: logTableForm.traceIdColumn || null,
    scanMode: logTableForm.scanMode || 'ID_INCREMENT',
    qualityCheckIntervalSeconds: logTableForm.qualityCheckIntervalSeconds,
    enabled: logTableForm.enabled,
    qualityRules: {
      noDataMinutes: logTableForm.qualityRules.noDataMinutes,
      emptyResponseThreshold: logTableForm.qualityRules.emptyResponseThreshold,
      duplicateThreshold: logTableForm.qualityRules.duplicateThreshold,
      responseBodyMaxLength: logTableForm.qualityRules.responseBodyMaxLength,
      rowCountUpperBound: logTableForm.qualityRules.rowCountUpperBound,
      requiredColumns: splitCsv(logTableForm.requiredColumnsText),
      successStatusCodes: splitCsv(logTableForm.successStatusCodesText)
        .map((item) => Number(item))
        .filter((item) => !Number.isNaN(item))
    }
  }
}

function splitCsv(value: string) {
  return String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}
</script>

<style scoped>
.monitor-config-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.summary-card {
  height: 100%;
}

.summary-row {
  margin-bottom: 0;
}

.summary-desc {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.page-titlebar,
.section-title,
.tab-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  margin: 0 0 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-transform: uppercase;
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
  color: var(--el-text-color-secondary);
}

.query-panel,
.panel-card {
  padding: 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.tab-toolbar {
  align-items: center;
  margin-bottom: 14px;
}

.table-main {
  font-weight: 700;
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

@media (max-width: 1200px) {
  .summary-row :deep(.el-col) {
    margin-bottom: 16px;
  }
}
</style>
