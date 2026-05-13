const EMPTY_TEXT = '-'

function firstValue(...values) {
  return values.find((value) => value !== null && value !== undefined && value !== '')
}

function formatNumber(value) {
  const resolved = firstValue(value)
  if (resolved === undefined) return EMPTY_TEXT
  const numeric = Number(resolved)
  if (!Number.isFinite(numeric)) return EMPTY_TEXT
  return numeric.toLocaleString()
}

function formatDuration(value) {
  const resolved = firstValue(value)
  if (resolved === undefined) return EMPTY_TEXT
  const numeric = Number(resolved)
  if (!Number.isFinite(numeric)) return EMPTY_TEXT
  if (numeric >= 1000) return `${(numeric / 1000).toFixed(2)} s`
  return `${numeric} ms`
}

function formatPercent(value) {
  const resolved = firstValue(value)
  if (resolved === undefined) return EMPTY_TEXT
  const numeric = Number(resolved)
  if (!Number.isFinite(numeric)) return EMPTY_TEXT
  return numeric > 1 ? `${numeric.toFixed(1)}%` : `${(numeric * 100).toFixed(1)}%`
}

function slowSqlStatusLabel(status) {
  const map = {
    OPEN: '待处理',
    NEW: '待处理',
    CONFIRMED: '已确认',
    IGNORED: '已忽略',
    FIXED: '已修复',
  }
  return map[status] || status || '待处理'
}

function slowSqlStatusType(status) {
  if (status === 'CONFIRMED') return 'warning'
  if (status === 'IGNORED') return 'info'
  if (status === 'FIXED') return 'success'
  return 'danger'
}

function issueLevelType(level) {
  if (level === 'CRITICAL' || level === 'HIGH' || level === 'P1') return 'danger'
  if (level === 'MEDIUM' || level === 'P2') return 'warning'
  return 'info'
}

export function unwrapSettledValue(result, fallback = {}) {
  if (result.status !== 'fulfilled') return fallback
  return result.value || fallback
}

export function unwrapSettledList(result) {
  if (result.status !== 'fulfilled') return []
  const value = result.value
  if (Array.isArray(value)) return value
  if (Array.isArray(value?.records)) return value.records
  if (Array.isArray(value?.items)) return value.items
  return []
}

export function buildMonitorStatCards(dashboard, slowSqlEvents, poolRisks, logQualityIssues) {
  const slowSqlCount = dashboard.slowSqlCount ?? slowSqlEvents.length
  const poolRiskCount = dashboard.poolRiskCount ?? poolRisks.length
  const logQualityIssueCount = dashboard.logQualityIssueCount ?? logQualityIssues.length

  return [
    {
      key: 'health',
      label: '健康应用',
      value: dashboard.healthyAppCount ?? 0,
      hint: `监控应用 ${dashboard.monitoredAppCount ?? 0}`,
      tone: 'ok',
    },
    {
      key: 'slowSql',
      label: '慢 SQL',
      value: slowSqlCount,
      hint: '含长时间未响应 SQL',
      tone: slowSqlCount ? 'danger' : 'ok',
    },
    {
      key: 'pool',
      label: '连接池风险',
      value: poolRiskCount,
      hint: 'HikariCP / Druid',
      tone: poolRiskCount ? 'warn' : 'ok',
    },
    {
      key: 'logQuality',
      label: '日志质量问题',
      value: logQualityIssueCount,
      hint: '接口日志表巡检',
      tone: logQualityIssueCount ? 'warn' : 'ok',
    },
  ]
}

export function normalizeSlowSqlEvent(row = {}) {
  const durationMs = firstValue(row.queryTimeMs, row.durationMs)
  return {
    ...row,
    displayDuration: formatDuration(durationMs),
    displayQps: formatNumber(row.qps),
    displayRowsExamined: formatNumber(row.rowsExamined),
    displaySqlPreview: firstValue(row.sqlDigest, row.maskedSql, row.sqlText, EMPTY_TEXT),
    displayDatasource: firstValue(row.datasourceCode, row.datasourceName, EMPTY_TEXT),
    displayLockLabel: row.lockRisk ? '存在锁风险' : '未发现锁风险',
    displayLockSummary: firstValue(row.lockSummary, row.transactionSummary, EMPTY_TEXT),
    displayIndexSuggestion: firstValue(row.indexSuggestion, row.suggestedIndexSql, '暂无建议'),
    displayStatusLabel: slowSqlStatusLabel(row.status),
    displayStatusType: slowSqlStatusType(row.status),
  }
}

export function normalizePoolRisk(row = {}) {
  return {
    ...row,
    displayPoolName: firstValue(row.poolName, row.instanceId, EMPTY_TEXT),
    displayActiveConnections: firstValue(row.activeConnections, EMPTY_TEXT),
    displayMaxConnections: firstValue(row.maxConnections, EMPTY_TEXT),
    displayUsagePercent: formatPercent(row.usagePercent),
    displayWaitingThreads: firstValue(row.waitingThreads, 0),
    displayTimeoutCount: firstValue(row.timeoutCount, 0),
    displayRiskLevel: firstValue(row.riskLevel, 'INFO'),
    displayRiskReason: firstValue(row.riskReason, EMPTY_TEXT),
  }
}

export function normalizeLogQualityIssue(row = {}) {
  return {
    ...row,
    displayTableName: firstValue(row.tableName, row.configName, EMPTY_TEXT),
    displayIssueType: firstValue(row.issueType, EMPTY_TEXT),
    displayIssueTagType: issueLevelType(row.issueLevel),
    displayEmptyResponseRate: formatPercent(row.emptyResponseRate),
    displayMissingRequestIdRate: formatPercent(row.missingRequestIdRate),
    displaySummary: firstValue(row.issueSummary, row.description, EMPTY_TEXT),
    displaySuggestion: firstValue(row.suggestion, EMPTY_TEXT),
  }
}
