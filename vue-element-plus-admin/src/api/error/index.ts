import request from '@/axios'
import { toPageRequest, toTableListResponse } from '@/api/common/page'
import type { PageResult } from '@/api/common/page'
import type {
  ErrorAdjustSeverityPayload,
  ErrorDetailResponse,
  ErrorEventItem,
  ErrorListResponse,
  ErrorManualActionPayload,
  ErrorManualConclusionPayload,
  ErrorPageParams,
  ErrorStatsItem,
  ErrorTypeOptionItem,
  ErrorTypeRuleFormModel,
  ErrorTypeRuleItem,
  ErrorTypeRulePayload,
  ErrorTypeRuleQueryParams
} from './types'

const ERROR_API_BASE = '/api/v1/errors'
const ERROR_TYPE_RULE_API_BASE = '/api/v1/error-type-rules'

const trimToUndefined = (value?: string) => {
  const trimmed = String(value || '').trim()
  return trimmed ? trimmed : undefined
}

const toRuleQueryParams = (params?: ErrorTypeRuleQueryParams) => {
  return {
    errorType: trimToUndefined(params?.errorType),
    enabled: params?.enabled,
    keyword: trimToUndefined(params?.keyword)
  }
}

const toErrorQueryParams = (params?: ErrorPageParams) => {
  return {
    ...toPageRequest(params),
    appName: trimToUndefined(params?.appName),
    environment: trimToUndefined(params?.environment),
    severity: trimToUndefined(params?.severity),
    status: trimToUndefined(params?.status),
    keyword: trimToUndefined(params?.keyword)
  }
}

const normalizeErrorDetail = (data?: Partial<ErrorDetailResponse>): ErrorDetailResponse => {
  return {
    event: data?.event || null,
    analysis: data?.analysis || null,
    analysisTasks: data?.analysisTasks || [],
    contextLogs: data?.contextLogs || [],
    notifications: data?.notifications || [],
    knowledgeMatches: data?.knowledgeMatches || [],
    sourceLocation: data?.sourceLocation || {}
  }
}

export const buildEmptyErrorTypeRuleForm = (): ErrorTypeRuleFormModel => ({
  ruleName: '',
  errorType: 'UNKNOWN',
  matchField: 'EXCEPTION_CLASS',
  matchMode: 'EXACT',
  pattern: '',
  priority: 100,
  enabled: true,
  builtin: false,
  remark: ''
})

export const toErrorTypeRuleForm = (item?: ErrorTypeRuleItem): ErrorTypeRuleFormModel => {
  const defaults = buildEmptyErrorTypeRuleForm()
  return {
    id: item?.id,
    ruleName: item?.ruleName || defaults.ruleName,
    errorType: item?.errorType || defaults.errorType,
    matchField: item?.matchField || defaults.matchField,
    matchMode: item?.matchMode || defaults.matchMode,
    pattern: item?.pattern || defaults.pattern,
    priority: Number(item?.priority ?? defaults.priority),
    enabled: item?.enabled !== false,
    builtin: item?.builtin === true,
    remark: item?.remark || ''
  }
}

export const toErrorTypeRulePayload = (form: ErrorTypeRuleFormModel): ErrorTypeRulePayload => {
  return {
    ruleName: String(form.ruleName || '').trim(),
    errorType: String(form.errorType || '')
      .trim()
      .toUpperCase(),
    matchField: String(form.matchField || '')
      .trim()
      .toUpperCase(),
    matchMode: String(form.matchMode || '')
      .trim()
      .toUpperCase(),
    pattern: String(form.pattern || '').trim(),
    priority: Number(form.priority || 100),
    enabled: form.enabled !== false,
    builtin: form.builtin === true,
    remark: trimToUndefined(form.remark) || null
  }
}

export const getErrorTypeRuleListApi = (params?: ErrorTypeRuleQueryParams) => {
  return request.get<ErrorTypeRuleItem[]>({
    url: ERROR_TYPE_RULE_API_BASE,
    params: toRuleQueryParams(params)
  })
}

export const getErrorTypeOptionsApi = () => {
  return request.get<ErrorTypeOptionItem[]>({
    url: `${ERROR_TYPE_RULE_API_BASE}/types`
  })
}

export const saveErrorTypeRuleApi = (form: ErrorTypeRuleFormModel) => {
  const payload = toErrorTypeRulePayload(form)
  return form.id
    ? request.put<ErrorTypeRuleItem>({
        url: `${ERROR_TYPE_RULE_API_BASE}/${form.id}`,
        data: payload
      })
    : request.post<ErrorTypeRuleItem>({
        url: ERROR_TYPE_RULE_API_BASE,
        data: payload
      })
}

export const deleteErrorTypeRuleApi = (id: string | number) => {
  return request.delete<{ id: string | number }>({
    url: `${ERROR_TYPE_RULE_API_BASE}/${id}`
  })
}

export const getErrorPageApi = (params: ErrorPageParams): Promise<IResponse<ErrorListResponse>> => {
  return request
    .post<PageResult<ErrorEventItem>>({
      url: `${ERROR_API_BASE}/page`,
      data: toErrorQueryParams(params)
    })
    .then(toTableListResponse<ErrorEventItem>)
}

export const getErrorStatsApi = () => {
  return request.get<ErrorStatsItem>({
    url: `${ERROR_API_BASE}/stats`
  })
}

export const getErrorDetailApi = (id: string | number): Promise<IResponse<ErrorDetailResponse>> => {
  return request
    .get<Partial<ErrorDetailResponse>>({
      url: `${ERROR_API_BASE}/${id}`
    })
    .then((response) => ({
      ...response,
      data: normalizeErrorDetail(response.data)
    }))
}

export const analyzeErrorApi = (id: string | number) => {
  return request.post({
    url: `${ERROR_API_BASE}/${id}/analyze`
  })
}

export const retryAnalyzeErrorApi = (id: string | number) => {
  return request.post({
    url: `${ERROR_API_BASE}/${id}/retry`
  })
}

export const retryNotifyErrorApi = (id: string | number) => {
  return request.post({
    url: `${ERROR_API_BASE}/${id}/retry-notify`
  })
}

export const ignoreErrorApi = (id: string | number, payload: ErrorManualActionPayload) => {
  return request.post({
    url: `${ERROR_API_BASE}/${id}/ignore`,
    data: payload
  })
}

export const markFalsePositiveApi = (id: string | number, payload: ErrorManualActionPayload) => {
  return request.post({
    url: `${ERROR_API_BASE}/${id}/mark-false-positive`,
    data: payload
  })
}

export const adjustSeverityApi = (id: string | number, payload: ErrorAdjustSeverityPayload) => {
  return request.post({
    url: `${ERROR_API_BASE}/${id}/adjust-severity`,
    data: payload
  })
}

export const saveManualConclusionApi = (
  id: string | number,
  payload: ErrorManualConclusionPayload
) => {
  return request.post({
    url: `${ERROR_API_BASE}/${id}/manual-conclusion`,
    data: payload
  })
}
