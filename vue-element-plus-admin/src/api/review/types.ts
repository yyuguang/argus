import type { BasePageParams, TableListResponse } from '@/api/common/page'

export interface ReviewTaskPageParams extends BasePageParams {
  scmProvider?: string
  status?: string
  keyword?: string
}

export interface ReviewTaskItem {
  id?: string | number
  scmProvider?: string
  scmConfigId?: string | number
  projectId?: number
  projectName?: string
  repoOwner?: string
  repoName?: string
  mrIid?: number
  mrTitle?: string
  mrUrl?: string
  authorId?: string
  authorName?: string
  sourceBranch?: string
  targetBranch?: string
  lastCommitSha?: string
  status?: string
  totalScore?: number
  scoreLevel?: string
  fileCount?: number
  addedLines?: number
  removedLines?: number
  criticalCount?: number
  majorCount?: number
  minorCount?: number
  tokensUsed?: number
  duration?: number
  errorMessage?: string
  summary?: string
  notified?: boolean
  createTime?: string
  updateTime?: string
}

export interface ReviewIssueItem {
  id?: string | number
  severity?: string
  category?: string
  filePath?: string
  startLine?: number
  endLine?: number
  description?: string
  suggestion?: string
  codeSnippet?: string
}

export interface ReviewTaskDetailResponse {
  task?: ReviewTaskItem
  issues?: ReviewIssueItem[]
}

export type ReviewTaskListResponse = TableListResponse<ReviewTaskItem>
