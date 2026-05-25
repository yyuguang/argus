import type { BasePageParams, TableListResponse } from '@/api/common/page'

export type AppLinkageHealthStatus = 'READY' | 'INCOMPLETE' | 'SCM_MISSING'

export type AppLinkageCodeIndexStatus =
  | 'SUCCESS'
  | 'RUNNING'
  | 'FAILED'
  | 'WARNING'
  | 'BOUND'
  | 'BUILDING'
  | 'NOT_INDEXED'

export interface ErrorAlertRouteModel {
  enabled: boolean
  channel: string
  priority: string
}

export type ErrorAlertRouteMap = Record<string, ErrorAlertRouteModel>

export interface AppLinkagePageParams extends BasePageParams {
  scmProvider?: string
  scmConfigId?: string | number
  healthStatus?: AppLinkageHealthStatus
  keyword?: string
}

export interface AppLinkageScmOption {
  id: string | number
  scmProvider: string
  projectId: number
  projectName: string
  repoOwner: string
  repoName: string
  enabled: boolean
  reviewConfig?: string
  defaultSourceRoot: string
  defaultBasePackage: string
  errorAlertRoutes: ErrorAlertRouteMap
}

export interface AppLinkageListItem {
  id?: string | number
  appName: string
  environment?: string
  scmConfigId?: string | number
  scmProvider?: string
  scmProjectId?: number | null
  projectName?: string
  repoOwner?: string
  repoName?: string
  scmEnabled?: boolean
  sourceRoot?: string
  basePackage?: string
  defaultBranch?: string
  activeVersionName?: string
  activeCommitSha?: string
  codeIndexStatus?: AppLinkageCodeIndexStatus
  healthStatus: AppLinkageHealthStatus
  healthLabel: string
  healthDescription: string
  errorAlertRoutes: ErrorAlertRouteMap
  createTime?: string
  updateTime?: string
}

export type AppLinkageListResponse = TableListResponse<AppLinkageListItem>

export interface AppLinkageFormModel {
  id?: string | number
  scmConfigId?: string | number
  appName: string
  sourceRoot: string
  basePackage: string
  defaultBranch: string
  errorAlertRoutes: ErrorAlertRouteMap
}

export interface AppLinkageVersionBindingFormModel {
  mappingId?: string | number
  appName: string
  environment: string
  scmConfigId?: string | number
  branchName: string
  commitSha: string
  versionName: string
  remark: string
}
