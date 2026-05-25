import request from '@/axios'
import type {
  KnowledgeActionPayload,
  KnowledgeAuditItem,
  KnowledgeEntryItem,
  KnowledgeSearchParams,
  KnowledgeSummaryItem
} from './types'

const KNOWLEDGE_API_BASE = '/api/v1/knowledge'

export const getKnowledgeEntriesApi = (params: KnowledgeSearchParams) => {
  return request.get<KnowledgeEntryItem[]>({
    url: `${KNOWLEDGE_API_BASE}/entries`,
    params
  })
}

export const getKnowledgeEntryDetailApi = (id: string | number) => {
  return request.get<KnowledgeEntryItem>({
    url: `${KNOWLEDGE_API_BASE}/entries/${id}`
  })
}

export const getKnowledgeAuditApi = (id: string | number) => {
  return request.get<KnowledgeAuditItem[]>({
    url: `${KNOWLEDGE_API_BASE}/entries/${id}/audit`
  })
}

export const getKnowledgeWhitelistCandidatesApi = (minOccurrence = 5) => {
  return request.get<KnowledgeEntryItem[]>({
    url: `${KNOWLEDGE_API_BASE}/whitelist-candidates`,
    params: { minOccurrence }
  })
}

export const getKnowledgeHighFrequencyApi = (hours = 1, minOccurrences = 5, limit = 20) => {
  return request.get<KnowledgeSummaryItem[]>({
    url: `${KNOWLEDGE_API_BASE}/summaries/high-frequency`,
    params: { hours, minOccurrences, limit }
  })
}

export const getKnowledgeNewFingerprintsApi = (hours = 1, limit = 20) => {
  return request.get<KnowledgeSummaryItem[]>({
    url: `${KNOWLEDGE_API_BASE}/summaries/new-fingerprints`,
    params: { hours, limit }
  })
}

export const getKnowledgeSurgingFingerprintsApi = (hours = 1, minIncrease = 5, limit = 20) => {
  return request.get<KnowledgeSummaryItem[]>({
    url: `${KNOWLEDGE_API_BASE}/summaries/surging-fingerprints`,
    params: { hours, minIncrease, limit }
  })
}

const postKnowledgeAction = (
  url: string,
  payload: KnowledgeActionPayload
): Promise<IResponse<KnowledgeEntryItem>> => {
  return request.post({
    url,
    params: payload
  })
}

export const confirmKnowledgeEntryApi = (id: string | number, payload: KnowledgeActionPayload) =>
  postKnowledgeAction(`${KNOWLEDGE_API_BASE}/entries/${id}/confirm`, payload)

export const markKnowledgeFalsePositiveApi = (
  id: string | number,
  payload: KnowledgeActionPayload
) => postKnowledgeAction(`${KNOWLEDGE_API_BASE}/entries/${id}/false-positive`, payload)

export const ignoreKnowledgeEntryApi = (id: string | number, payload: KnowledgeActionPayload) =>
  postKnowledgeAction(`${KNOWLEDGE_API_BASE}/entries/${id}/ignore`, payload)

export const promoteKnowledgeWhitelistApi = (id: string | number, operator: string) =>
  request.post<KnowledgeEntryItem>({
    url: `${KNOWLEDGE_API_BASE}/entries/${id}/promote-whitelist`,
    params: { operator }
  })

export const demoteKnowledgeWhitelistApi = (id: string | number, operator: string) =>
  request.post<KnowledgeEntryItem>({
    url: `${KNOWLEDGE_API_BASE}/entries/${id}/demote-whitelist`,
    params: { operator }
  })
