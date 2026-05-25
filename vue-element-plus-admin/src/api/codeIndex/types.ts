import type { BasePageParams, TableListResponse } from '@/api/common/page'

export type CodeIndexId = string | number

export type CodeIndexScanStatus = 'PENDING' | 'RUNNING' | 'PARTIAL' | 'SUCCESS' | 'FAILED' | string

export type CodeIndexScanTaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELED'
  | 'REUSED'
  | string

export type CodeIndexScanStage =
  | 'WAITING'
  | 'SCM_READING'
  | 'MODULE_SCANNING'
  | 'SOURCE_ROOT_DISCOVERING'
  | 'JAVA_PARSING'
  | 'INDEX_AGGREGATING'
  | 'INDEX_PERSISTING'
  | 'COMPLETED'
  | 'FAILED'
  | string

export type CodeIndexScanType = 'FULL' | 'INCREMENTAL' | 'MODULE_RESCAN' | 'REBUILD' | string

export type CodeIndexTriggerType =
  | 'FIRST_INIT'
  | 'WEBHOOK'
  | 'MANUAL'
  | 'DEPLOY_CALLBACK'
  | 'SCHEDULED'
  | string

export type CodeIndexConfidence = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE' | string

export type CodeIndexMatchType =
  | 'QUALIFIED_NAME'
  | 'FILE_PATH'
  | 'SIMPLE_NAME'
  | 'PACKAGE_PREFIX'
  | 'FALLBACK'
  | 'NONE'
  | string

export type CodeClassKind = 'CLASS' | 'INTERFACE' | 'ENUM' | 'ANNOTATION' | 'RECORD' | string

export interface CodeIndexPageParams extends BasePageParams {
  scmConfigId?: CodeIndexId
  branchName?: string
  scanStatus?: CodeIndexScanStatus
  stale?: boolean
  keyword?: string
}

export interface CodeClassPageParams extends BasePageParams {
  indexId?: CodeIndexId
  modulePath?: string
  packageName?: string
  className?: string
  qualifiedName?: string
  filePath?: string
  classKind?: CodeClassKind
  parserStatus?: CodeIndexScanStatus
  confidence?: CodeIndexConfidence
}

export interface CodeIndexScanPayload {
  branchName?: string
  commitSha?: string
  baseCommitSha?: string
  scanType?: CodeIndexScanType
  forceRebuild?: boolean
  reason?: string
  filePaths?: string[]
  deletedFilePaths?: string[]
  sourceRootOverrides?: string[]
}

export interface CodeIndexScanTaskCreateReq {
  branchName?: string
  commitSha?: string
  scanType?: CodeIndexScanType
  forceRebuild?: boolean
  reason?: string
}

export interface CodeIndexScanTask {
  taskId?: CodeIndexId
  taskNo?: string
  taskStatus?: CodeIndexScanTaskStatus
  scanStage?: CodeIndexScanStage
  progressPercent?: number
  stageMessage?: string
  loadedFileCount?: number
  totalJavaFileCount?: number
  parsedFileCount?: number
  failedFileCount?: number
  classCount?: number
  packageCount?: number
  warningCount?: number
  resultIndexId?: CodeIndexId
  reusedIndexId?: CodeIndexId
  latestErrorMessage?: string
  message?: string
  startedAt?: string
  finishedAt?: string
  lastHeartbeatAt?: string
}

export interface CodeIndexSummaryItem {
  indexId?: CodeIndexId
  scmConfigId?: CodeIndexId
  scmProvider?: string
  scmProjectId?: string
  repoOwner?: string
  repoName?: string
  branchName?: string
  commitSha?: string
  indexVersion?: number
  scanStatus?: CodeIndexScanStatus
  scanType?: CodeIndexScanType
  triggerType?: CodeIndexTriggerType
  moduleCount?: number
  sourceRootCount?: number
  javaFileCount?: number
  classCount?: number
  packageCount?: number
  ambiguousPackageCount?: number
  warningCount?: number
  confidence?: CodeIndexConfidence
  stale?: boolean
  latestErrorMessage?: string
  startedAt?: string
  finishedAt?: string
  createTime?: string
  updateTime?: string
}

export interface CodeIndexModuleSummary {
  moduleId?: CodeIndexId
  moduleName?: string
  modulePath?: string
  parentModulePath?: string
  buildType?: string
  packaging?: string
  sourceRootsJson?: string
  javaFileCount?: number
  classCount?: number
  scanStatus?: CodeIndexScanStatus
  warningMessage?: string
}

export interface CodeIndexPackageSummary {
  packageName?: string
  primaryModulePath?: string
  modulePathsJson?: string
  classCount?: number
  ambiguous?: boolean
  confidence?: CodeIndexConfidence
}

export interface CodeIndexScanWarning {
  level?: string
  message?: string
  modulePath?: string
  filePath?: string
}

export interface CodeIndexDetailResponse {
  indexSummary?: CodeIndexSummaryItem
  modules?: CodeIndexModuleSummary[]
  packages?: CodeIndexPackageSummary[]
  warnings?: CodeIndexScanWarning[]
}

export interface CodeClassIndexItem {
  id?: CodeIndexId
  indexId?: CodeIndexId
  scmConfigId?: CodeIndexId
  modulePath?: string
  sourceRoot?: string
  filePath?: string
  fileSha?: string
  packageName?: string
  className?: string
  qualifiedName?: string
  classKind?: CodeClassKind
  primaryType?: boolean
  lineStart?: number
  lineEnd?: number
  importsJson?: string
  parserStatus?: CodeIndexScanStatus
  confidence?: CodeIndexConfidence
}

export interface SourceLocatePayload {
  appName?: string
  environment?: string
  scmConfigId?: CodeIndexId
  branchName?: string
  commitSha?: string
  qualifiedName?: string
  filePath?: string
  lineNumber?: number
}

export interface SourceLocateCandidate {
  indexId?: CodeIndexId
  commitSha?: string
  modulePath?: string
  sourceRoot?: string
  filePath?: string
  packageName?: string
  className?: string
  qualifiedName?: string
  confidence?: CodeIndexConfidence
  matchType?: CodeIndexMatchType
}

export interface SourceLocateResponse extends SourceLocateCandidate {
  matched?: boolean
  lineNumber?: number
  warnings?: string[]
  candidates?: SourceLocateCandidate[]
}

export interface AppVersionBindingPayload {
  mappingId?: CodeIndexId
  appName?: string
  environment?: string
  scmConfigId?: CodeIndexId
  branchName?: string
  commitSha?: string
  versionName?: string
  bindingSource?: CodeIndexTriggerType
  remark?: string
}

export interface AppVersionBindingQuery {
  appName: string
  environment: string
  scmConfigId: CodeIndexId
}

export interface AppVersionBindingItem {
  bindingId?: CodeIndexId
  mappingId?: CodeIndexId
  appName?: string
  environment?: string
  scmConfigId?: CodeIndexId
  indexId?: CodeIndexId
  branchName?: string
  commitSha?: string
  versionName?: string
  bindingSource?: CodeIndexTriggerType
  indexStatus?: CodeIndexScanStatus
  active?: boolean
  activatedAt?: string
  lastSeenAt?: string
  createTime?: string
  updateTime?: string
}

export type CodeIndexListResponse = TableListResponse<CodeIndexSummaryItem>

export type CodeClassListResponse = TableListResponse<CodeClassIndexItem>
