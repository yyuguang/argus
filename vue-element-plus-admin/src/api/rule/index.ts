import request from '@/axios'
import { toPageRequest, toTableListResponse } from '@/api/common/page'
import type { PageResult } from '@/api/common/page'
import type {
  PromptCatalogCategoryItem,
  PromptTemplateQueryParams,
  PromptTemplateSavePayload,
  PromptTemplateSchemeItem,
  RuleDocumentActionPayload,
  RuleDocumentDetailItem,
  RuleDocumentImportPayload,
  RuleDocumentItem,
  RuleDocumentListResponse,
  RuleDocumentPageParams,
  RuleDocumentPreviewItem,
  RuleProfileItem,
  RuleStandardsMigrationPayload,
  RuleStandardsMigrationResult
} from './types'

const RULE_DOCUMENT_API_BASE = '/api/v1/rules/documents'
const RULE_PROMPT_API_BASE = '/api/v1/rules/prompts'
const RULE_PROFILE_API_BASE = '/api/v1/rules/profiles/scm'

const normalizeNullable = (value: unknown) => {
  if (value === '' || value === undefined) {
    return null
  }
  return value
}

export const buildEmptyRuleProfile = (): RuleProfileItem => ({
  scmConfigId: undefined,
  scoringProfile: {
    blockThreshold: 60,
    blockingRules: {
      criticalDirectBlock: true,
      majorBlockThreshold: null,
      suggestionOnlyBlockEnabled: false
    },
    dimensions: {
      compliance: 20,
      correctness: 25,
      dataIntegrity: 20,
      performance: 15,
      maintainability: 20
    },
    severityDefinitions: {
      CRITICAL: {
        deduction: 20,
        label: '致命',
        examples: ['SQL注入', '硬编码密钥', '死锁风险', '数据丢失风险']
      },
      MAJOR: {
        deduction: 10,
        label: '严重',
        examples: ['未处理异常', 'N+1查询', '空指针风险', '事务缺失']
      },
      MINOR: {
        deduction: 3,
        label: '一般',
        examples: ['魔法数字', '过长方法', '无效import', '命名不规范']
      },
      SUGGESTION: {
        deduction: 0,
        label: '建议',
        examples: ['Optional替代null检查', '日志补充']
      }
    }
  },
  ruleProfile: {
    standardCategories: ['CODING', 'API', 'DATABASE', 'SECURITY', 'CUSTOM']
  }
})

const deepMerge = <T extends Record<string, any>>(base: T, override?: Record<string, any>): T => {
  const result = Array.isArray(base) ? ([...base] as any) : ({ ...base } as T)
  if (!override || typeof override !== 'object') {
    return result
  }
  Object.keys(override).forEach((key) => {
    const value = override[key]
    if (Array.isArray(value)) {
      result[key] = [...value]
    } else if (value && typeof value === 'object' && !Array.isArray(result[key])) {
      result[key] = deepMerge(result[key] || {}, value)
    } else if (value !== undefined) {
      result[key] = value
    }
  })
  return result
}

export const normalizeRuleProfile = (data?: Partial<RuleProfileItem>): RuleProfileItem => {
  return deepMerge(buildEmptyRuleProfile(), data || {})
}

export const normalizeRuleDocumentDetail = (
  data?: Partial<RuleDocumentDetailItem>
): RuleDocumentDetailItem => {
  return {
    id: data?.id,
    documentCode: data?.documentCode || '',
    documentName: data?.documentName || '',
    category: data?.category || '',
    scope: data?.scope || '',
    scmConfigId: data?.scmConfigId,
    scmProjectName: data?.scmProjectName || '',
    status: data?.status || '',
    parseStatus: data?.parseStatus || '',
    vectorStatus: data?.vectorStatus || '',
    chunkCount: data?.chunkCount ?? 0,
    latestErrorMessage: data?.latestErrorMessage || '',
    updateTime: data?.updateTime || '',
    sourceType: data?.sourceType || '',
    fileName: data?.fileName || '',
    fileExt: data?.fileExt || '',
    summaryText: data?.summaryText || '',
    versionNo: data?.versionNo ?? 0,
    remark: data?.remark || '',
    createBy: data?.createBy || '',
    createTime: data?.createTime || '',
    updateBy: data?.updateBy || ''
  }
}

export const normalizeRuleDocumentPreview = (
  data?: Partial<RuleDocumentPreviewItem>
): RuleDocumentPreviewItem => {
  return {
    plainText: data?.plainText || '',
    chunks: data?.chunks || [],
    parseStatus: data?.parseStatus || '',
    vectorStatus: data?.vectorStatus || '',
    latestErrorMessage: data?.latestErrorMessage || ''
  }
}

const toRuleDocumentPageParams = (params?: RuleDocumentPageParams) => {
  return {
    ...toPageRequest(params),
    category: params?.category || undefined,
    scope: params?.scope || undefined,
    scmConfigId: normalizeNullable(params?.scmConfigId),
    status: params?.status || undefined,
    parseStatus: params?.parseStatus || undefined,
    vectorStatus: params?.vectorStatus || undefined,
    keyword: params?.keyword || undefined
  }
}

export const getRuleDocumentsPageApi = (
  params: RuleDocumentPageParams
): Promise<IResponse<RuleDocumentListResponse>> => {
  return request
    .post<PageResult<RuleDocumentItem>>({
      url: `${RULE_DOCUMENT_API_BASE}/page`,
      data: toRuleDocumentPageParams(params)
    })
    .then(toTableListResponse<RuleDocumentItem>)
}

export const getRuleDocumentPageApi = getRuleDocumentsPageApi

export const importRuleDocumentApi = (file: File, payload: RuleDocumentImportPayload) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('category', payload.category)
  formData.append('scope', payload.scope)
  formData.append('documentName', payload.documentName)
  if (
    payload.scmConfigId !== undefined &&
    payload.scmConfigId !== null &&
    payload.scmConfigId !== ''
  ) {
    formData.append('scmConfigId', String(payload.scmConfigId))
  }
  if (payload.remark) {
    formData.append('remark', payload.remark)
  }
  formData.append('activeAfterImport', String(payload.activeAfterImport === true))

  return request.post<RuleDocumentDetailItem>({
    url: `${RULE_DOCUMENT_API_BASE}/import`,
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const getRuleDocumentDetailApi = (id: string | number) => {
  return request.get<RuleDocumentDetailItem>({
    url: `${RULE_DOCUMENT_API_BASE}/${id}`
  })
}

export const getRuleDocumentPreviewApi = (id: string | number) => {
  return request.get<RuleDocumentPreviewItem>({
    url: `${RULE_DOCUMENT_API_BASE}/${id}/preview`
  })
}

const postRuleDocumentAction = (
  id: string | number,
  action: 'activate' | 'disable' | 'reindex',
  payload?: RuleDocumentActionPayload
) => {
  return request.post<RuleDocumentDetailItem>({
    url: `${RULE_DOCUMENT_API_BASE}/${id}/${action}`,
    data: {
      operator: payload?.operator || undefined,
      comment: payload?.comment || undefined
    }
  })
}

export const activateRuleDocumentApi = (id: string | number, payload?: RuleDocumentActionPayload) =>
  postRuleDocumentAction(id, 'activate', payload)

export const disableRuleDocumentApi = (id: string | number, payload?: RuleDocumentActionPayload) =>
  postRuleDocumentAction(id, 'disable', payload)

export const reindexRuleDocumentApi = (id: string | number, payload?: RuleDocumentActionPayload) =>
  postRuleDocumentAction(id, 'reindex', payload)

export const migrateRuleStandardsApi = (payload?: RuleStandardsMigrationPayload) => {
  return request.post<RuleStandardsMigrationResult>({
    url: `${RULE_DOCUMENT_API_BASE}/migrations/standards`,
    data: {
      activeAfterImport: payload?.activeAfterImport === true
    }
  })
}

export const getPromptCatalogApi = (params?: PromptTemplateQueryParams) => {
  return request.get<PromptCatalogCategoryItem[]>({
    url: `${RULE_PROMPT_API_BASE}/catalog`,
    params: {
      category: params?.category || undefined,
      keyword: params?.keyword || undefined
    }
  })
}

export const getPromptGlobalSchemesApi = (params?: PromptTemplateQueryParams) => {
  return request.get<PromptTemplateSchemeItem[]>({
    url: `${RULE_PROMPT_API_BASE}/global`,
    params: {
      category: params?.category || undefined,
      keyword: params?.keyword || undefined
    }
  })
}

export const getPromptScmSchemesApi = (
  scmConfigId: string | number,
  params?: PromptTemplateQueryParams
) => {
  return request.get<PromptTemplateSchemeItem[]>({
    url: `${RULE_PROMPT_API_BASE}/scm/${scmConfigId}`,
    params: {
      category: params?.category || undefined,
      keyword: params?.keyword || undefined
    }
  })
}

export const getPromptTemplateDetailApi = (
  templateCode: string,
  scope: 'GLOBAL' | 'SCM',
  scmConfigId?: string | number
) => {
  return request.get<PromptTemplateSchemeItem>({
    url: `${RULE_PROMPT_API_BASE}/${templateCode}`,
    params: {
      scope,
      scmConfigId: scope === 'SCM' ? normalizeNullable(scmConfigId) : undefined
    }
  })
}

export const getPromptEffectiveTemplateApi = (
  templateCode: string,
  scmConfigId?: string | number
) => {
  return request.get<PromptTemplateSchemeItem>({
    url: `${RULE_PROMPT_API_BASE}/${templateCode}/effective`,
    params: {
      scmConfigId: normalizeNullable(scmConfigId)
    }
  })
}

export const savePromptGlobalSchemeApi = (
  templateCode: string,
  payload: PromptTemplateSavePayload
) => {
  return request.put<PromptTemplateSchemeItem>({
    url: `${RULE_PROMPT_API_BASE}/global/${templateCode}`,
    data: payload
  })
}

export const savePromptScmSchemeApi = (
  scmConfigId: string | number,
  templateCode: string,
  payload: PromptTemplateSavePayload
) => {
  return request.put<PromptTemplateSchemeItem>({
    url: `${RULE_PROMPT_API_BASE}/scm/${scmConfigId}/${templateCode}`,
    data: payload
  })
}

export const deletePromptScmOverrideApi = (scmConfigId: string | number, templateCode: string) => {
  return request.delete({
    url: `${RULE_PROMPT_API_BASE}/scm/${scmConfigId}/${templateCode}`
  })
}

export const getScmRuleProfileApi = (scmConfigId: string | number) => {
  return request
    .get<RuleProfileItem>({
      url: `${RULE_PROFILE_API_BASE}/${scmConfigId}`
    })
    .then((response) => ({
      ...response,
      data: normalizeRuleProfile(response.data)
    }))
}

export const getRuleProfileApi = getScmRuleProfileApi

export const saveScmRuleProfileApi = (scmConfigId: string | number, payload: RuleProfileItem) => {
  return request.put<RuleProfileItem>({
    url: `${RULE_PROFILE_API_BASE}/${scmConfigId}`,
    data: {
      scmConfigId: payload.scmConfigId,
      scoringProfile: payload.scoringProfile,
      ruleProfile: payload.ruleProfile
    }
  })
}

export const saveRuleProfileApi = saveScmRuleProfileApi
