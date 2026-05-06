import http from './http'

export function fetchScmConfigs() {
  return http.get('/api/v1/scm/configs')
}

export function createScmConfig(payload) {
  return http.post('/api/v1/scm/configs', payload)
}

export function updateScmConfig(id, payload) {
  return http.put(`/api/v1/scm/configs/${id}`, payload)
}
