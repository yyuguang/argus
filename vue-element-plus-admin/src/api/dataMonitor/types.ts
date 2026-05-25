export interface MonitorQueryParams {
  appName?: string
  environment?: string
  window?: string
}

export interface MonitorActionPayload {
  operator: string
  reason?: string | null
}

export interface SlowSqlConfirmPayload {
  operator: string
  confirmedCauseType?: string | null
  confirmedConclusion?: string | null
  acceptedIndexSuggestion?: boolean
}

export interface DataMonitorDashboardItem {
  healthyAppCount?: number
  monitoredAppCount?: number
  slowSqlCount?: number
  poolRiskCount?: number
  logQualityIssueCount?: number
  [key: string]: any
}

export interface SlowSqlEventItem {
  id?: string | number
  appName?: string
  environment?: string
  datasourceCode?: string
  datasourceName?: string
  queryTimeMs?: number
  durationMs?: number
  lockTimeMs?: number
  qps?: number
  rowsSent?: number
  rowsExamined?: number
  sqlDigest?: string
  maskedSql?: string
  sqlTextMasked?: string
  sqlText?: string
  lockRisk?: boolean
  lockSummary?: string
  transactionSummary?: string
  indexSuggestion?: string
  suggestedIndexSql?: string
  indexSuggestionSql?: string
  status?: string
  riskLevel?: string
  causeType?: string
  sourceType?: string
  processState?: string
  optimizationSuggestion?: string
  analysisStatus?: string
  needDba?: boolean
  needDeveloper?: boolean
  explainRows?: Array<Record<string, any>>
  explainJson?: string
  relatedLockEvent?: Record<string, any>
  lockEvent?: Record<string, any>
  poolSnapshot?: Record<string, any>
  relatedPoolSnapshot?: Record<string, any>
  rootCause?: string
  analysisSummary?: string
  canViewFullSql?: boolean
  [key: string]: any
}

export interface PoolRiskItem {
  id?: string | number
  appName?: string
  poolName?: string
  instanceId?: string
  poolType?: string
  activeConnections?: number
  maxConnections?: number
  usagePercent?: number
  waitingThreads?: number
  timeoutCount?: number
  riskLevel?: string
  riskReason?: string
  [key: string]: any
}

export interface LogQualityIssueItem {
  id?: string | number
  appName?: string
  tableName?: string
  configName?: string
  issueType?: string
  issueLevel?: string
  emptyResponseRate?: number
  missingRequestIdRate?: number
  issueSummary?: string
  description?: string
  suggestion?: string
  [key: string]: any
}

export interface DataMonitorConfigOverview {
  id?: string | number
  scmConfigId?: string | number
  projectMappingId?: string | number
  appName?: string
  environment?: string
  enabled?: boolean
  ownerTeam?: string
  techOwner?: string
  alertWebhookMode?: string
  defaultRuntimeCollectIntervalSeconds?: number
  defaultPoolMetricPushIntervalSeconds?: number
  defaultLogQualityCheckIntervalSeconds?: number
  alertScanIntervalSeconds?: number
  remark?: string
  [key: string]: any
}

export interface DataMonitorConfigPayload {
  enabled: boolean
  ownerTeam?: string | null
  techOwner?: string | null
  alertWebhookMode?: string | null
  defaultRuntimeCollectIntervalSeconds?: number | null
  defaultPoolMetricPushIntervalSeconds?: number | null
  defaultLogQualityCheckIntervalSeconds?: number | null
  alertScanIntervalSeconds?: number | null
  remark?: string | null
}

export interface DataSourceThresholds {
  longSqlSeconds?: number
  longTransactionSeconds?: number
  lockWaitSeconds?: number
  connectionUsagePercent?: number
}

export interface DataSourceCollectOptions {
  processlist?: boolean
  innodbTransaction?: boolean
  innodbLock?: boolean
  globalStatus?: boolean
  explain?: boolean
  fullSql?: boolean
}

export interface DataSourceConfigItem {
  id?: string | number
  monitorConfigId?: string | number
  mappingId?: string | number
  datasourceCode?: string
  datasourceName?: string
  dbType?: string
  dbVersion?: string
  jdbcUrl?: string
  host?: string
  port?: number
  databaseName?: string
  username?: string
  password?: string
  readonly?: boolean
  enabled?: boolean
  collectProcesslist?: boolean
  collectInnodbTrx?: boolean
  collectInnodbLock?: boolean
  collectGlobalStatus?: boolean
  explainEnabled?: boolean
  fullSqlCollectEnabled?: boolean
  runtimeCollectIntervalSeconds?: number
  poolMetricPushIntervalSeconds?: number
  thresholds?: DataSourceThresholds
  collectOptions?: DataSourceCollectOptions
  [key: string]: any
}

export interface DataSourceConfigPayload {
  datasourceCode: string
  datasourceName?: string | null
  dbType?: string | null
  dbVersion?: string | null
  jdbcUrl: string
  host?: string | null
  port?: number | null
  databaseName?: string | null
  username: string
  password?: string | null
  readonly?: boolean
  enabled?: boolean
  runtimeCollectIntervalSeconds?: number | null
  poolMetricPushIntervalSeconds?: number | null
  thresholds?: DataSourceThresholds
  collectOptions?: DataSourceCollectOptions
}

export interface DataSourceTestPayload {
  jdbcUrl?: string
  username?: string
  password?: string | null
}

export interface DataSourceTestResult {
  connected?: boolean
  readonlyVerified?: boolean
  canExplain?: boolean
  canReadProcesslist?: boolean
  canReadInnodbStatus?: boolean
  message?: string
  [key: string]: any
}

export interface SlowLogConfigItem {
  id?: string | number
  datasourceId?: string | number
  enabled?: boolean
  sourceType?: string
  logPath?: string
  charset?: string
  minQueryTimeMs?: number
  collectFullSql?: boolean
  collectIntervalSeconds?: number
  cursorOffset?: number
  lastCollectedAt?: string
}

export interface SlowLogConfigPayload {
  enabled?: boolean
  sourceType?: string | null
  logPath?: string | null
  charset?: string | null
  minQueryTimeMs?: number | null
  collectFullSql?: boolean
  collectIntervalSeconds?: number | null
  cursorOffset?: number | null
}

export interface LogQualityRules {
  noDataMinutes?: number
  emptyResponseThreshold?: number
  duplicateThreshold?: number
  responseBodyMaxLength?: number
  rowCountUpperBound?: number
  requiredColumns?: string[]
  successStatusCodes?: number[]
  [key: string]: any
}

export interface LogTableConfigItem {
  id?: string | number
  monitorConfigId?: string | number
  datasourceId?: string | number
  appName?: string
  environment?: string
  configName?: string
  tableName?: string
  primaryKeyColumn?: string
  interfaceCodeColumn?: string
  requestTimeColumn?: string
  responseTimeColumn?: string
  responseBodyColumn?: string
  statusCodeColumn?: string
  requestIdColumn?: string
  traceIdColumn?: string
  scanMode?: string
  qualityCheckIntervalSeconds?: number
  lastScanValue?: string
  enabled?: boolean
  requiredColumns?: string[]
  qualityRules?: LogQualityRules
  alertRules?: string
  [key: string]: any
}

export interface LogTableConfigPayload {
  datasourceId?: string | number | null
  configName?: string | null
  tableName?: string | null
  primaryKeyColumn?: string | null
  interfaceCodeColumn?: string | null
  requestTimeColumn?: string | null
  responseTimeColumn?: string | null
  responseBodyColumn?: string | null
  statusCodeColumn?: string | null
  requestIdColumn?: string | null
  traceIdColumn?: string | null
  scanMode?: string | null
  qualityCheckIntervalSeconds?: number | null
  enabled?: boolean
  qualityRules?: LogQualityRules
  alertRules?: string | null
}

export interface MonitorStatCard {
  key: string
  label: string
  value: number | string
  hint: string
  tone: 'ok' | 'warn' | 'danger'
}
