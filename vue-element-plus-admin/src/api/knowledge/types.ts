export interface KnowledgeEntryItem {
  id?: string | number
  errorFingerprint?: string
  errorType?: string
  appName?: string
  title?: string
  errorPattern?: string
  rootCause?: string
  fixSuggestion?: string
  preventionAdvice?: string
  sourceEventId?: number
  sourceAnalysisId?: number
  status?: string
  source?: string
  confirmedBy?: string
  confirmedAt?: string
  occurrenceCount?: number
  lastOccurredAt?: string
  tagsJson?: string
  createTime?: string
  updateTime?: string
}

export interface KnowledgeAuditItem {
  id?: string | number
  knowledgeEntryId?: string | number
  action?: string
  operator?: string
  comment?: string
  beforeStatus?: string
  afterStatus?: string
  createTime?: string
}

export interface KnowledgeSummaryItem {
  errorFingerprint?: string
  appName?: string
  errorType?: string
  severity?: string
  sourceType?: string
  interfaceRef?: string
  eventCount?: number
  occurrenceTotal?: number
  previousOccurrenceTotal?: number
  increaseTotal?: number
  firstOccurredAt?: string
  lastOccurredAt?: string
}

export interface KnowledgeSearchParams {
  status?: string
  errorType?: string
  appName?: string
}

export interface KnowledgeActionPayload {
  operator: string
  comment?: string
}
