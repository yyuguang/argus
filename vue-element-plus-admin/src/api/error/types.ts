import type { BasePageParams, TableListResponse } from '@/api/common/page'
import type { KnowledgeEntryItem } from '@/api/knowledge/types'

export interface ErrorTypeOptionItem {
  value: string
  label: string
}

export interface ErrorPageParams extends BasePageParams {
  appName?: string
  environment?: string
  severity?: string
  status?: string
  keyword?: string
}

export interface ErrorEventItem {
  id?: string | number
  logId?: string
  appName?: string
  errorType?: string
  errorMessage?: string
  severity?: string
  errorFingerprint?: string
  className?: string
  methodName?: string
  lineNumber?: number
  filePath?: string
  businessKey?: string
  interfaceRef?: string
  traceId?: string
  rawStackTrace?: string
  analyzed?: boolean
  notified?: boolean
  occurredAt?: string
  environment?: string
  hostName?: string
  occurrenceCount?: number
  firstOccurredAt?: string
  lastOccurredAt?: string
  lastBusinessKey?: string
  lastTraceId?: string
  processingStatus?: string
  analysisDecision?: string
  initialSeverity?: string
  finalSeverity?: string
  severitySource?: string
  severityReason?: string
  severityConfidence?: number | string
  escalationCount?: number
  lastEscalationReason?: string
  ownerTeam?: string
  sourceType?: string
  createTime?: string
  updateTime?: string
}

export interface ErrorStatsItem {
  total?: number
  unanalyzed?: number
  ignored?: number
  falsePositive?: number
  severityCounts?: Record<string, number>
}

export interface ErrorAnalysisItem {
  id?: string | number
  errorEventId?: string | number
  rootCause?: string
  technicalDetail?: string
  impactScope?: string
  finalSeverity?: string
  fixDescription?: string
  fixCodeExample?: string
  fixFilePath?: string
  fixLineRange?: string
  estimatedEffort?: string
  preventionAdvice?: string
  confidence?: number | string
  tokensUsed?: number
  duration?: number
  aiModel?: string
  source?: string
  manualConclusion?: string
  createTime?: string
  updateTime?: string
}

export interface ErrorContextLogItem {
  id?: string | number
  errorEventId?: string | number
  logTime?: string
  logLevel?: string
  loggerName?: string
  threadName?: string
  traceId?: string
  message?: string
  sortOrder?: number
}

export interface ErrorNotificationItem {
  id?: string | number
  type?: string
  channel?: string
  refId?: string | number
  refType?: string
  contentSummary?: string
  status?: string
  errorMessage?: string
  retryCount?: number
  sentAt?: string
  createTime?: string
  updateTime?: string
}

export interface ErrorAnalysisTaskItem {
  id?: string | number
  errorEventId?: string | number
  triggerType?: string
  status?: string
  analysisId?: string | number
  aiModel?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  durationMs?: number
  createTime?: string
  updateTime?: string
}

export interface ErrorSourceLocationItem {
  appName?: string
  className?: string
  methodName?: string
  filePath?: string
  lineNumber?: number
  interfaceRef?: string
  sourceType?: string
}

export interface ErrorDetailResponse {
  event: ErrorEventItem | null
  analysis: ErrorAnalysisItem | null
  analysisTasks: ErrorAnalysisTaskItem[]
  contextLogs: ErrorContextLogItem[]
  notifications: ErrorNotificationItem[]
  knowledgeMatches: KnowledgeEntryItem[]
  sourceLocation: ErrorSourceLocationItem
}

export interface ErrorManualActionPayload {
  operator?: string
  reason?: string
}

export interface ErrorAdjustSeverityPayload {
  severity: string
  reason?: string
}

export interface ErrorManualConclusionPayload {
  rootCause?: string
  severity?: string
  fixDescription?: string
  preventionAdvice?: string
}

export type ErrorListResponse = TableListResponse<ErrorEventItem>

export interface ErrorTypeRuleQueryParams {
  errorType?: string
  enabled?: boolean
  keyword?: string
}

export interface ErrorTypeRuleItem {
  id?: string | number
  ruleName?: string
  errorType?: string
  matchField?: string
  matchMode?: string
  pattern?: string
  priority?: number
  enabled?: boolean
  builtin?: boolean
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface ErrorTypeRuleFormModel {
  id?: string | number
  ruleName: string
  errorType: string
  matchField: string
  matchMode: string
  pattern: string
  priority: number
  enabled: boolean
  builtin: boolean
  remark: string
}

export interface ErrorTypeRulePayload {
  ruleName: string
  errorType: string
  matchField: string
  matchMode: string
  pattern: string
  priority: number
  enabled: boolean
  builtin: boolean
  remark: string | null
}
