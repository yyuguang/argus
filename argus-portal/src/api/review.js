import http from './http'

export function fetchReviewTasks(params) {
  return http.get('/api/v1/review/tasks', { params })
}

export function fetchReviewTaskDetail(id) {
  return http.get(`/api/v1/review/tasks/${id}`)
}
