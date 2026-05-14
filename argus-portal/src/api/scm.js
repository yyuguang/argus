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

export function fetchProjectMappings(params = {}) {
  return http.get('/api/v1/registry/project-mappings', { params })
}

export function createProjectMapping(payload) {
  return http.post('/api/v1/registry/project-mappings', payload)
}

export function updateProjectMapping(id, payload) {
  return http.put(`/api/v1/registry/project-mappings/${id}`, payload)
}

export function deleteProjectMapping(id) {
  return http.delete(`/api/v1/registry/project-mappings/${id}`)
}
