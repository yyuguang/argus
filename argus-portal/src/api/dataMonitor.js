import http from './http'

function mappingBase(scmConfigId, mappingId) {
  return `/api/v1/scm/configs/${scmConfigId}/app-mappings/${mappingId}/data-monitor`
}

export function fetchDataMonitorOverview(scmConfigId, mappingId) {
  return http.get(mappingBase(scmConfigId, mappingId))
}

export function saveDataMonitorOverview(scmConfigId, mappingId, payload) {
  return http.put(mappingBase(scmConfigId, mappingId), payload)
}

export function fetchDataSources(scmConfigId, mappingId) {
  return http.get(`${mappingBase(scmConfigId, mappingId)}/datasources`)
}

export function createDataSource(scmConfigId, mappingId, payload) {
  return http.post(`${mappingBase(scmConfigId, mappingId)}/datasources`, payload)
}

export function updateDataSource(scmConfigId, mappingId, datasourceId, payload) {
  return http.put(`${mappingBase(scmConfigId, mappingId)}/datasources/${datasourceId}`, payload)
}

export function setDataSourceEnabled(scmConfigId, mappingId, datasourceId, enabled) {
  return http.put(`${mappingBase(scmConfigId, mappingId)}/datasources/${datasourceId}/enabled`, { enabled })
}

export function testDataSource(scmConfigId, mappingId, payload) {
  return http.post(`${mappingBase(scmConfigId, mappingId)}/datasources/test`, payload)
}

export function testExistingDataSource(scmConfigId, mappingId, datasourceId, payload) {
  return http.post(`${mappingBase(scmConfigId, mappingId)}/datasources/${datasourceId}/test`, payload)
}

export function fetchSlowLogConfig(scmConfigId, mappingId, datasourceId) {
  return http.get(`${mappingBase(scmConfigId, mappingId)}/datasources/${datasourceId}/slow-log`)
}

export function saveSlowLogConfig(scmConfigId, mappingId, datasourceId, payload) {
  return http.put(`${mappingBase(scmConfigId, mappingId)}/datasources/${datasourceId}/slow-log`, payload)
}

export function fetchLogTables(scmConfigId, mappingId) {
  return http.get(`${mappingBase(scmConfigId, mappingId)}/log-tables`)
}

export function createLogTable(scmConfigId, mappingId, payload) {
  return http.post(`${mappingBase(scmConfigId, mappingId)}/log-tables`, payload)
}

export function updateLogTable(scmConfigId, mappingId, configId, payload) {
  return http.put(`${mappingBase(scmConfigId, mappingId)}/log-tables/${configId}`, payload)
}

export function setLogTableEnabled(scmConfigId, mappingId, configId, enabled) {
  return http.put(`${mappingBase(scmConfigId, mappingId)}/log-tables/${configId}/enabled`, { enabled })
}

export function fetchDataMonitorDashboard(params = {}) {
  return http.get('/api/v1/data-monitor/dashboard', { params })
}

export function fetchSlowSqlEvents(params = {}) {
  return http.get('/api/v1/data-monitor/slow-sql', { params })
}

export function fetchPoolRisks(params = {}) {
  return http.get('/api/v1/data-monitor/pools/risks', { params })
}

export function fetchLogQualityIssues(params = {}) {
  return http.get('/api/v1/data-monitor/log-quality/issues', { params })
}

export function ignoreSlowSql(id, payload) {
  return http.post(`/api/v1/data-monitor/slow-sql/${id}/ignore`, payload)
}

export function confirmSlowSql(id, payload) {
  return http.post(`/api/v1/data-monitor/slow-sql/${id}/confirm`, payload)
}

export function ignoreLogQualityIssue(id, payload) {
  return http.post(`/api/v1/data-monitor/log-quality/issues/${id}/ignore`, payload)
}
