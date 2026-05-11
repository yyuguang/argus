import http from './http'

export function fetchReviewerProfiles(params) {
  return http.get('/api/v1/review/profiles', { params })
}

export function fetchReviewerProfile(authorName, params) {
  return http.get(`/api/v1/review/profiles/${encodeURIComponent(authorName)}`, { params })
}

export function fetchReviewerTrend(authorName, params) {
  return http.get(`/api/v1/review/profiles/${encodeURIComponent(authorName)}/trend`, { params })
}

export function fetchTeamTopIssues(params) {
  return http.get('/api/v1/review/profiles/top-issues', { params })
}
