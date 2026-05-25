import type {
  DataMonitorDashboardItem,
  LogQualityIssueItem,
  MonitorStatCard,
  PoolRiskItem,
  SlowSqlEventItem
} from './types'

const EMPTY_TEXT = '-'

const firstValue = (...values: any[]) =>
  values.find((value) => value !== null && value !== undefined && value !== '')

const formatNumber = (value: unknown) => {
  const resolved = firstValue(value)
  if (resolved === undefined) return EMPTY_TEXT
  const numeric = Number(resolved)
  if (!Number.isFinite(numeric)) return EMPTY_TEXT
  return numeric.toLocaleString()
}

const formatDuration = (value: unknown) => {
  const resolved = firstValue(value)
  if (resolved === undefined) return EMPTY_TEXT
  const numeric = Number(resolved)
  if (!Number.isFinite(numeric)) return EMPTY_TEXT
  if (numeric >= 1000) return `${(numeric / 1000).toFixed(2)} s`
  return `${numeric} ms`
}

const formatSeconds = (value: unknown) => {
  const resolved = firstValue(value)
  if (resolved === undefined) return EMPTY_TEXT
  const numeric = Number(resolved)
  if (!Number.isFinite(numeric)) return EMPTY_TEXT
  return `${numeric} s`
}

const formatPercent = (value: unknown) => {
  const resolved = firstValue(value)
  if (resolved === undefined) return EMPTY_TEXT
  const numeric = Number(resolved)
  if (!Number.isFinite(numeric)) return EMPTY_TEXT
  return numeric > 1 ? `${numeric.toFixed(1)}%` : `${(numeric * 100).toFixed(1)}%`
}

const parseJsonArray = (value: unknown) => {
  if (Array.isArray(value)) return value
  if (!value || typeof value !== 'string') return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const slowSqlStatusLabel = (status?: string) => {
  const map: Record<string, string> = {
    OPEN: '待处理',
    NEW: '待处理',
    CONFIRMED: '已确认',
    IGNORED: '已忽略',
    FIXED: '已修复'
  }
  return map[status || ''] || status || '待处理'
}

const slowSqlStatusType = (status?: string) => {
  if (status === 'CONFIRMED') return 'warning'
  if (status === 'IGNORED') return 'info'
  if (status === 'FIXED') return 'success'
  return 'danger'
}

const issueLevelType = (level?: string) => {
  if (level === 'CRITICAL' || level === 'HIGH' || level === 'P1') return 'danger'
  if (level === 'MEDIUM' || level === 'P2') return 'warning'
  return 'info'
}

export const buildMonitorStatCards = (
  dashboard: DataMonitorDashboardItem,
  slowSqlEvents: SlowSqlEventItem[],
  poolRisks: PoolRiskItem[],
  logQualityIssues: LogQualityIssueItem[]
): MonitorStatCard[] => {
  const slowSqlCount = dashboard.slowSqlCount ?? slowSqlEvents.length
  const poolRiskCount = dashboard.poolRiskCount ?? poolRisks.length
  const logQualityIssueCount = dashboard.logQualityIssueCount ?? logQualityIssues.length

  return [
    {
      key: 'health',
      label: '健康应用',
      value: dashboard.healthyAppCount ?? 0,
      hint: `监控应用 ${dashboard.monitoredAppCount ?? 0}`,
      tone: 'ok'
    },
    {
      key: 'slowSql',
      label: '慢 SQL',
      value: slowSqlCount,
      hint: '含长时间未响应 SQL',
      tone: slowSqlCount ? 'danger' : 'ok'
    },
    {
      key: 'pool',
      label: '连接池风险',
      value: poolRiskCount,
      hint: 'HikariCP / Druid',
      tone: poolRiskCount ? 'warn' : 'ok'
    },
    {
      key: 'logQuality',
      label: '日志质量问题',
      value: logQualityIssueCount,
      hint: '接口日志表巡检',
      tone: logQualityIssueCount ? 'warn' : 'ok'
    }
  ]
}

export const normalizeSlowSqlEvent = (row: SlowSqlEventItem = {}) => {
  const durationMs = firstValue(row.queryTimeMs, row.durationMs)
  const explainRows = parseJsonArray(row.explainRows || row.explainJson)
  const relatedLockEvent = row.relatedLockEvent || row.lockEvent || null
  const relatedPoolSnapshot = row.relatedPoolSnapshot || row.poolSnapshot || null
  return {
    ...row,
    displayDuration: formatDuration(durationMs),
    displayLockTime: formatDuration(row.lockTimeMs),
    displayQps: formatNumber(row.qps),
    displayRowsSent: formatNumber(row.rowsSent),
    displayRowsExamined: formatNumber(row.rowsExamined),
    displaySqlPreview: firstValue(
      row.sqlDigest,
      row.maskedSql,
      row.sqlTextMasked,
      row.sqlText,
      EMPTY_TEXT
    ),
    displayMaskedSql: firstValue(row.maskedSql, row.sqlTextMasked, row.sqlDigest, EMPTY_TEXT),
    displayDatasource: firstValue(row.datasourceCode, row.datasourceName, EMPTY_TEXT),
    displayLockLabel: row.lockRisk ? '存在锁风险' : '未发现锁风险',
    displayLockSummary: firstValue(row.lockSummary, row.transactionSummary, EMPTY_TEXT),
    displayIndexSuggestion: firstValue(
      row.indexSuggestion,
      row.suggestedIndexSql,
      row.indexSuggestionSql,
      '暂无建议'
    ),
    displayStatusLabel: slowSqlStatusLabel(row.status),
    displayStatusType: slowSqlStatusType(row.status),
    displayRiskLevel: firstValue(row.riskLevel, EMPTY_TEXT),
    displayCauseType: firstValue(row.causeType, EMPTY_TEXT),
    displaySourceType: firstValue(row.sourceType, EMPTY_TEXT),
    displayProcessState: firstValue(row.processState, EMPTY_TEXT),
    displayOptimizationSuggestion: firstValue(row.optimizationSuggestion, EMPTY_TEXT),
    displayAnalysisStatus: firstValue(row.analysisStatus, EMPTY_TEXT),
    displayNeedDba: row.needDba === true ? '需要 DBA 介入' : '暂不需要 DBA',
    displayNeedDeveloper: row.needDeveloper === true ? '需要研发处理' : '暂不需要研发',
    explainRows,
    hasExplainRows: explainRows.length > 0,
    relatedLockEvent,
    relatedPoolSnapshot,
    displayRelatedLockId: firstValue(
      (row as any).relatedLockEventId,
      relatedLockEvent?.id,
      EMPTY_TEXT
    ),
    displayRelatedPoolId: firstValue(
      (row as any).relatedPoolSnapshotId,
      relatedPoolSnapshot?.id,
      EMPTY_TEXT
    ),
    hasLockContext: Boolean(
      (row as any).relatedLockEventId ||
        relatedLockEvent ||
        row.lockRisk ||
        row.lockTimeMs ||
        row.processState
    ),
    hasPoolContext: Boolean(
      (row as any).relatedPoolSnapshotId ||
        relatedPoolSnapshot ||
        (row as any).poolRiskType ||
        (row as any).poolRiskLevel
    ),
    displayLockWaitSeconds: formatSeconds(relatedLockEvent?.waitSeconds),
    displayLockTable: firstValue(relatedLockEvent?.lockTable, EMPTY_TEXT),
    displayLockIndex: firstValue(relatedLockEvent?.lockIndex, EMPTY_TEXT),
    displayLockType: firstValue(relatedLockEvent?.lockType, EMPTY_TEXT),
    displayBlockingProcessId: firstValue(relatedLockEvent?.blockingProcessId, EMPTY_TEXT),
    displayWaitingProcessId: firstValue(relatedLockEvent?.waitingProcessId, EMPTY_TEXT),
    displayPoolType: firstValue(relatedPoolSnapshot?.poolType, (row as any).poolType, EMPTY_TEXT),
    displayPoolRiskType: firstValue(
      relatedPoolSnapshot?.riskType,
      (row as any).poolRiskType,
      EMPTY_TEXT
    ),
    displayPoolRiskLevel: firstValue(
      relatedPoolSnapshot?.riskLevel,
      (row as any).poolRiskLevel,
      EMPTY_TEXT
    ),
    displayPoolActiveConnections: firstValue(relatedPoolSnapshot?.activeConnections, EMPTY_TEXT),
    displayPoolMaxConnections: firstValue(relatedPoolSnapshot?.maxConnections, EMPTY_TEXT),
    displayPoolWaitingThreads: firstValue(relatedPoolSnapshot?.waitingThreads, EMPTY_TEXT),
    displayPoolTimeoutCount: firstValue(relatedPoolSnapshot?.timeoutCount, EMPTY_TEXT)
  }
}

export const normalizePoolRisk = (row: PoolRiskItem = {}) => {
  return {
    ...row,
    displayPoolName: firstValue(row.poolName, row.instanceId, EMPTY_TEXT),
    displayActiveConnections: firstValue(row.activeConnections, EMPTY_TEXT),
    displayMaxConnections: firstValue(row.maxConnections, EMPTY_TEXT),
    displayUsagePercent: formatPercent(row.usagePercent),
    displayWaitingThreads: firstValue(row.waitingThreads, 0),
    displayTimeoutCount: firstValue(row.timeoutCount, 0),
    displayRiskLevel: firstValue(row.riskLevel, 'INFO'),
    displayRiskReason: firstValue(row.riskReason, EMPTY_TEXT)
  }
}

export const normalizeLogQualityIssue = (row: LogQualityIssueItem = {}) => {
  return {
    ...row,
    displayTableName: firstValue(row.tableName, row.configName, EMPTY_TEXT),
    displayIssueType: firstValue(row.issueType, EMPTY_TEXT),
    displayIssueTagType: issueLevelType(row.issueLevel),
    displayEmptyResponseRate: formatPercent(row.emptyResponseRate),
    displayMissingRequestIdRate: formatPercent(row.missingRequestIdRate),
    displaySummary: firstValue(row.issueSummary, row.description, EMPTY_TEXT),
    displaySuggestion: firstValue(row.suggestion, EMPTY_TEXT)
  }
}
