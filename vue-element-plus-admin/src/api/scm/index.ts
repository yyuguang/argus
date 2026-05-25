import request from '@/axios'
import { toPageRequest, toTableListResponse } from '@/api/common/page'
import type { PageResult } from '@/api/common/page'
import type {
  ProjectMappingItem,
  ScmConfigItem,
  ScmConfigListResponse,
  ScmConfigPageParams
} from './types'

const SCM_CONFIG_API_BASE = '/api/v1/scm/configs'

const normalizeNullable = (value: unknown) => {
  if (value === '' || value === undefined) {
    return null
  }
  return value
}

const normalizeEnabledFlag = (value?: number | boolean) => {
  if (typeof value === 'boolean') {
    return value ? 1 : 0
  }
  return value
}

const toScmConfigPayload = (data: ScmConfigItem) => {
  return {
    scmProvider: data.scmProvider,
    projectId: normalizeNullable(data.projectId),
    projectName: normalizeNullable(data.projectName),
    repoOwner: normalizeNullable(data.repoOwner),
    repoName: normalizeNullable(data.repoName),
    apiBaseUrl: normalizeNullable(data.apiBaseUrl),
    webBaseUrl: normalizeNullable(data.webBaseUrl),
    accessToken: normalizeNullable(data.accessToken),
    webhookSecret: normalizeNullable(data.webhookSecret),
    basePackages: normalizeNullable(data.basePackages),
    moduleSourceRoots: normalizeNullable(data.moduleSourceRoots),
    packageModuleMappings: normalizeNullable(data.packageModuleMappings),
    maxRelatedClasses: normalizeNullable(data.maxRelatedClasses),
    maxContextTokens: normalizeNullable(data.maxContextTokens),
    reviewParallelism: normalizeNullable(data.reviewParallelism),
    enabled: data.enabled !== false,
    description: normalizeNullable(data.description),
    wechatNotifyEnabled: normalizeEnabledFlag(data.wechatNotifyEnabled),
    wechatNotifyWebhook: normalizeNullable(data.wechatNotifyWebhook),
    feishuNotifyEnabled: normalizeEnabledFlag(data.feishuNotifyEnabled),
    feishuNotifyWebhook: normalizeNullable(data.feishuNotifyWebhook),
    dingtalkNotifyEnabled: normalizeEnabledFlag(data.dingtalkNotifyEnabled),
    dingtalkNotifyWebhook: normalizeNullable(data.dingtalkNotifyWebhook),
    reviewConfig: normalizeNullable(data.reviewConfig)
  }
}

const hasConfigId = (id: ScmConfigItem['id']) => id !== undefined && id !== null && id !== ''

export const getScmConfigPageApi = (
  params: ScmConfigPageParams
): Promise<IResponse<ScmConfigListResponse>> => {
  return request
    .post<PageResult<ScmConfigItem>>({
      url: `${SCM_CONFIG_API_BASE}/page`,
      data: toPageRequest(params)
    })
    .then(toTableListResponse<ScmConfigItem>)
}

export const listScmConfigsApi = (): Promise<IResponse<ScmConfigItem[]>> => {
  return request.get({ url: SCM_CONFIG_API_BASE })
}

export const getProjectMappingsApi = (params?: {
  appName?: string
  scmProvider?: string
}): Promise<IResponse<ProjectMappingItem[]>> => {
  return request.get({ url: '/api/v1/registry/project-mappings', params })
}

export const saveScmConfigApi = (data: ScmConfigItem) => {
  const configId = data.id
  const payload = toScmConfigPayload(data)

  return hasConfigId(configId)
    ? request.put({ url: `${SCM_CONFIG_API_BASE}/${configId}`, data: payload })
    : request.post({ url: SCM_CONFIG_API_BASE, data: payload })
}
