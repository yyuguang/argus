export interface ReviewerProfileItem {
  id?: string | number
  authorName?: string
  authorId?: string
  scmProvider?: string
  totalReviews?: number
  avgScore?: number | string
  dimensionStats?: string
  topIssueTags?: string
  topIssueRules?: string
  scoreTrend?: string
  recentReviews?: string
  firstReviewAt?: string
  lastReviewAt?: string
  createTime?: string
  updateTime?: string
}

export interface ReviewerTrendPoint {
  score?: number
}

export interface ReviewerTopIssueItem {
  rule?: string
  count?: number
  [key: string]: any
}
