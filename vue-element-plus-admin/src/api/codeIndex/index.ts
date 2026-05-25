import request from '@/axios'
import { toPageRequest, toTableListResponse } from '@/api/common/page'
import type { PageResult } from '@/api/common/page'
import type {
  AppVersionBindingItem,
  AppVersionBindingPayload,
  AppVersionBindingQuery,
  CodeClassIndexItem,
  CodeClassListResponse,
  CodeClassPageParams,
  CodeIndexDetailResponse,
  CodeIndexId,
  CodeIndexListResponse,
  CodeIndexPageParams,
  CodeIndexScanPayload,
  CodeIndexScanTask,
  CodeIndexScanTaskCreateReq,
  CodeIndexSummaryItem,
  SourceLocatePayload,
  SourceLocateResponse
} from './types'

const CODE_INDEX_API_BASE = '/api/v1/code-indexes'
const APP_VERSION_BINDING_API_BASE = `${CODE_INDEX_API_BASE}/app-version-bindings`

const trimToUndefined = (value?: string) => {
  const trimmed = String(value || '').trim()
  return trimmed ? trimmed : undefined
}

const normalizeId = (value?: CodeIndexId) => {
  if (value === undefined || value === null || value === '') {
    return undefined
  }
  return value
}

const toCodeIndexPagePayload = (params?: CodeIndexPageParams) => ({
  ...toPageRequest(params),
  scmConfigId: normalizeId(params?.scmConfigId),
  branchName: trimToUndefined(params?.branchName),
  scanStatus: trimToUndefined(params?.scanStatus),
  stale: params?.stale,
  keyword: trimToUndefined(params?.keyword)
})

const toCodeClassPagePayload = (params?: CodeClassPageParams) => ({
  ...toPageRequest(params),
  indexId: normalizeId(params?.indexId),
  modulePath: trimToUndefined(params?.modulePath),
  packageName: trimToUndefined(params?.packageName),
  className: trimToUndefined(params?.className),
  qualifiedName: trimToUndefined(params?.qualifiedName),
  filePath: trimToUndefined(params?.filePath),
  classKind: trimToUndefined(params?.classKind),
  parserStatus: trimToUndefined(params?.parserStatus),
  confidence: trimToUndefined(params?.confidence)
})

const toScanPayload = (payload?: CodeIndexScanPayload) => ({
  branchName: trimToUndefined(payload?.branchName),
  commitSha: trimToUndefined(payload?.commitSha),
  baseCommitSha: trimToUndefined(payload?.baseCommitSha),
  scanType: trimToUndefined(payload?.scanType),
  forceRebuild: payload?.forceRebuild,
  reason: trimToUndefined(payload?.reason),
  filePaths: payload?.filePaths || [],
  deletedFilePaths: payload?.deletedFilePaths || [],
  sourceRootOverrides: payload?.sourceRootOverrides || []
})

const toScanTaskCreatePayload = (payload?: CodeIndexScanTaskCreateReq) => ({
  branchName: trimToUndefined(payload?.branchName),
  commitSha: trimToUndefined(payload?.commitSha),
  scanType: trimToUndefined(payload?.scanType),
  forceRebuild: payload?.forceRebuild,
  reason: trimToUndefined(payload?.reason)
})

const toBindingPayload = (payload: AppVersionBindingPayload) => ({
  mappingId: normalizeId(payload.mappingId),
  appName: trimToUndefined(payload.appName),
  environment: trimToUndefined(payload.environment),
  scmConfigId: normalizeId(payload.scmConfigId),
  branchName: trimToUndefined(payload.branchName),
  commitSha: trimToUndefined(payload.commitSha),
  versionName: trimToUndefined(payload.versionName),
  bindingSource: trimToUndefined(payload.bindingSource),
  remark: trimToUndefined(payload.remark)
})

export const getLatestCodeIndexApi = (
  scmConfigId: CodeIndexId,
  branchName?: string
): Promise<IResponse<CodeIndexSummaryItem>> => {
  return request.get({
    url: `${CODE_INDEX_API_BASE}/scm/${scmConfigId}/latest`,
    params: { branchName: trimToUndefined(branchName) }
  })
}

export const getCodeIndexPageApi = (
  params?: CodeIndexPageParams
): Promise<IResponse<CodeIndexListResponse>> => {
  return request
    .post<PageResult<CodeIndexSummaryItem>>({
      url: `${CODE_INDEX_API_BASE}/page`,
      data: toCodeIndexPagePayload(params)
    })
    .then(toTableListResponse<CodeIndexSummaryItem>)
}

export const getCodeIndexDetailApi = (
  indexId: CodeIndexId
): Promise<IResponse<CodeIndexDetailResponse>> => {
  return request.get({ url: `${CODE_INDEX_API_BASE}/${indexId}` })
}

export const scanCodeIndexApi = (
  scmConfigId: CodeIndexId,
  payload?: CodeIndexScanPayload
): Promise<IResponse<CodeIndexSummaryItem>> => {
  return request.post({
    url: `${CODE_INDEX_API_BASE}/scm/${scmConfigId}/scan`,
    data: toScanPayload(payload)
  })
}

export const createCodeIndexScanTaskApi = (
  scmConfigId: CodeIndexId,
  payload?: CodeIndexScanTaskCreateReq
): Promise<IResponse<CodeIndexScanTask>> => {
  return request.post({
    url: `${CODE_INDEX_API_BASE}/scm/${scmConfigId}/scan-tasks`,
    data: toScanTaskCreatePayload(payload)
  })
}

export const getCodeIndexScanTaskApi = (
  taskId: CodeIndexId
): Promise<IResponse<CodeIndexScanTask>> => {
  return request.get({ url: `${CODE_INDEX_API_BASE}/scan-tasks/${taskId}` })
}

export const getRunningCodeIndexScanTaskApi = (
  scmConfigId: CodeIndexId,
  branchName?: string
): Promise<IResponse<CodeIndexScanTask | null>> => {
  return request.get({
    url: `${CODE_INDEX_API_BASE}/scm/${scmConfigId}/scan-tasks/running`,
    params: { branchName: trimToUndefined(branchName) }
  })
}

export const getCodeClassPageApi = (
  indexId: CodeIndexId,
  params?: CodeClassPageParams
): Promise<IResponse<CodeClassListResponse>> => {
  return request
    .post<PageResult<CodeClassIndexItem>>({
      url: `${CODE_INDEX_API_BASE}/${indexId}/classes/page`,
      data: toCodeClassPagePayload({ ...(params || {}), indexId })
    })
    .then(toTableListResponse<CodeClassIndexItem>)
}

export const locateSourceApi = (
  payload: SourceLocatePayload
): Promise<IResponse<SourceLocateResponse>> => {
  return request.post({ url: `${CODE_INDEX_API_BASE}/locate`, data: payload })
}

export const bindAppVersionApi = (
  payload: AppVersionBindingPayload
): Promise<IResponse<AppVersionBindingItem>> => {
  return request.post({ url: APP_VERSION_BINDING_API_BASE, data: toBindingPayload(payload) })
}

export const getCurrentAppVersionBindingApi = (
  params: AppVersionBindingQuery
): Promise<IResponse<AppVersionBindingItem>> => {
  return request.get({
    url: `${APP_VERSION_BINDING_API_BASE}/current`,
    params: {
      appName: params.appName,
      environment: params.environment,
      scmConfigId: params.scmConfigId
    }
  })
}
