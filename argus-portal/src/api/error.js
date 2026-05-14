import http from './http'

export function fetchErrorStats() {
  return http.get('/api/v1/errors/stats')
}

export function fetchErrors(params = {}) {
  return http.get('/api/v1/errors', { params })
}

export function fetchErrorDetail(id) {
  return http.get(`/api/v1/errors/${id}`)
}

export function fetchAnalysisTasks(id) {
  return http.get(`/api/v1/errors/${id}/analysis-tasks`)
}

export function fetchFingerprintEvents(fingerprint) {
  return http.get(`/api/v1/errors/fingerprints/${encodeURIComponent(fingerprint)}`)
}

export function analyzeError(id) {
  return http.post(`/api/v1/errors/${id}/analyze`)
}

export function retryAnalyzeError(id) {
  return http.post(`/api/v1/errors/${id}/retry`)
}

export function retryNotifyError(id) {
  return http.post(`/api/v1/errors/${id}/retry-notify`)
}

export function ignoreError(id, payload) {
  return http.post(`/api/v1/errors/${id}/ignore`, payload)
}

export function markFalsePositive(id, payload) {
  return http.post(`/api/v1/errors/${id}/mark-false-positive`, payload)
}

export function adjustSeverity(id, payload) {
  return http.post(`/api/v1/errors/${id}/adjust-severity`, payload)
}

export function saveManualConclusion(id, payload) {
  return http.post(`/api/v1/errors/${id}/manual-conclusion`, payload)
}
