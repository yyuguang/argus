import request from '@/axios'
import type { ReviewerProfileItem, ReviewerTopIssueItem } from './types'

const REVIEWER_PROFILE_API_BASE = '/api/v1/review/profiles'

export const getReviewerProfilesApi = (params: { scmProvider: string }) => {
  return request.get<ReviewerProfileItem[]>({
    url: REVIEWER_PROFILE_API_BASE,
    params
  })
}

export const getReviewerProfileDetailApi = (
  authorName: string,
  params: { scmProvider: string }
) => {
  return request.get<ReviewerProfileItem>({
    url: `${REVIEWER_PROFILE_API_BASE}/${encodeURIComponent(authorName)}`,
    params
  })
}

export const getReviewerTrendApi = (authorName: string, params: { scmProvider: string }) => {
  return request.get<number[]>({
    url: `${REVIEWER_PROFILE_API_BASE}/${encodeURIComponent(authorName)}/trend`,
    params
  })
}

export const getReviewerTopIssuesApi = (params: { scmProvider: string; days: number }) => {
  return request.get<ReviewerTopIssueItem[]>({
    url: `${REVIEWER_PROFILE_API_BASE}/top-issues`,
    params
  })
}
