import type { BasePageParams, TableListResponse } from '@/api/common/page'

export interface ScmConfigPageParams extends BasePageParams {
  scmProvider?: string
  enabled?: boolean
  keyword?: string
}

export interface ScmConfigItem {
  id?: string | number
  scmProvider?: string
  projectId?: number | null
  projectName?: string
  repoOwner?: string
  repoName?: string
  apiBaseUrl?: string
  webBaseUrl?: string
  accessToken?: string
  webhookSecret?: string
  basePackages?: string
  moduleSourceRoots?: string
  packageModuleMappings?: string
  maxRelatedClasses?: number | null
  maxContextTokens?: number | null
  reviewParallelism?: number | null
  enabled?: boolean
  description?: string
  wechatNotifyEnabled?: number | boolean
  wechatNotifyWebhook?: string
  feishuNotifyEnabled?: number | boolean
  feishuNotifyWebhook?: string
  dingtalkNotifyEnabled?: number | boolean
  dingtalkNotifyWebhook?: string
  reviewConfig?: string
  createTime?: string
  updateTime?: string
}

export type ScmConfigListResponse = TableListResponse<ScmConfigItem>

export interface ProjectMappingItem {
  id?: string | number
  appName?: string
  scmProvider?: string
  scmProjectId?: number | null
  sourceRoot?: string
  basePackage?: string
  defaultBranch?: string
  createTime?: string
  updateTime?: string
  [key: string]: any
}

export interface ReviewConfigModel {
  trigger: {
    enabled: boolean
    eventTypes: string[]
    branchMode: string
    targetBranches: string[]
    sourceBranches: string[]
  }
  fileFilter: {
    maxReviewFiles: number
    maxDiffLinesPerFile: number
    maxTotalDiffLines: number
    excludeFilePatterns: string[]
    binaryExtensions: string[]
  }
  scoring: {
    blockThreshold: number
    aiWeight: number
    ruleWeight: number
  }
  notification: {
    scoreAlertThreshold: number
    scoreAlertChannels: string[]
    platforms: Record<string, NotificationPlatformModel> & {
      wechat: NotificationPlatformModel
      feishu: NotificationPlatformModel
      dingtalk: NotificationPlatformModel
    }
    retry: {
      maxRetries: number
      timeoutSec: number
      backoffSeconds: number[]
    }
  }
  [key: string]: any
}

export interface NotificationPlatformModel {
  enabled: boolean
  webhook: string
}
