import request from '@/axios'
import type {
  DataMonitorConfigOverview,
  DataMonitorConfigPayload,
  DataMonitorDashboardItem,
  DataSourceConfigItem,
  DataSourceConfigPayload,
  DataSourceTestPayload,
  DataSourceTestResult,
  LogQualityIssueItem,
  LogTableConfigItem,
  LogTableConfigPayload,
  MonitorActionPayload,
  MonitorQueryParams,
  PoolRiskItem,
  SlowLogConfigItem,
  SlowLogConfigPayload,
  SlowSqlConfirmPayload,
  SlowSqlEventItem
} from './types'

const mappingBase = (scmConfigId: string | number, mappingId: string | number) =>
  `/api/v1/scm/configs/${scmConfigId}/app-mappings/${mappingId}/data-monitor`

export const fetchDataMonitorOverviewApi = (
  scmConfigId: string | number,
  mappingId: string | number
): Promise<IResponse<DataMonitorConfigOverview>> => {
  return request.get({ url: mappingBase(scmConfigId, mappingId) })
}

export const saveDataMonitorOverviewApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  payload: DataMonitorConfigPayload
): Promise<IResponse<DataMonitorConfigOverview>> => {
  return request.put({ url: mappingBase(scmConfigId, mappingId), data: payload })
}

export const fetchDataSourcesApi = (
  scmConfigId: string | number,
  mappingId: string | number
): Promise<IResponse<DataSourceConfigItem[]>> => {
  return request.get({ url: `${mappingBase(scmConfigId, mappingId)}/datasources` })
}

export const createDataSourceApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  payload: DataSourceConfigPayload
): Promise<IResponse<DataSourceConfigItem>> => {
  return request.post({ url: `${mappingBase(scmConfigId, mappingId)}/datasources`, data: payload })
}

export const updateDataSourceApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  datasourceId: string | number,
  payload: DataSourceConfigPayload
): Promise<IResponse<DataSourceConfigItem>> => {
  return request.put({
    url: `${mappingBase(scmConfigId, mappingId)}/datasources/${datasourceId}`,
    data: payload
  })
}

export const setDataSourceEnabledApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  datasourceId: string | number,
  enabled: boolean
): Promise<IResponse<DataSourceConfigItem>> => {
  return request.put({
    url: `${mappingBase(scmConfigId, mappingId)}/datasources/${datasourceId}/enabled`,
    data: { enabled }
  })
}

export const testDataSourceApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  payload: DataSourceTestPayload
): Promise<IResponse<DataSourceTestResult>> => {
  return request.post({
    url: `${mappingBase(scmConfigId, mappingId)}/datasources/test`,
    data: payload
  })
}

export const testExistingDataSourceApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  datasourceId: string | number,
  payload?: DataSourceTestPayload
): Promise<IResponse<DataSourceTestResult>> => {
  return request.post({
    url: `${mappingBase(scmConfigId, mappingId)}/datasources/${datasourceId}/test`,
    data: payload
  })
}

export const fetchSlowLogConfigApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  datasourceId: string | number
): Promise<IResponse<SlowLogConfigItem>> => {
  return request.get({
    url: `${mappingBase(scmConfigId, mappingId)}/datasources/${datasourceId}/slow-log`
  })
}

export const saveSlowLogConfigApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  datasourceId: string | number,
  payload: SlowLogConfigPayload
): Promise<IResponse<SlowLogConfigItem>> => {
  return request.put({
    url: `${mappingBase(scmConfigId, mappingId)}/datasources/${datasourceId}/slow-log`,
    data: payload
  })
}

export const fetchLogTablesApi = (
  scmConfigId: string | number,
  mappingId: string | number
): Promise<IResponse<LogTableConfigItem[]>> => {
  return request.get({ url: `${mappingBase(scmConfigId, mappingId)}/log-tables` })
}

export const createLogTableApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  payload: LogTableConfigPayload
): Promise<IResponse<LogTableConfigItem>> => {
  return request.post({ url: `${mappingBase(scmConfigId, mappingId)}/log-tables`, data: payload })
}

export const updateLogTableApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  configId: string | number,
  payload: LogTableConfigPayload
): Promise<IResponse<LogTableConfigItem>> => {
  return request.put({
    url: `${mappingBase(scmConfigId, mappingId)}/log-tables/${configId}`,
    data: payload
  })
}

export const setLogTableEnabledApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  configId: string | number,
  enabled: boolean
): Promise<IResponse<LogTableConfigItem>> => {
  return request.put({
    url: `${mappingBase(scmConfigId, mappingId)}/log-tables/${configId}/enabled`,
    data: { enabled }
  })
}

export const deleteLogTableApi = (
  scmConfigId: string | number,
  mappingId: string | number,
  configId: string | number
): Promise<IResponse<{ id: string | number }>> => {
  return request.delete({
    url: `${mappingBase(scmConfigId, mappingId)}/log-tables/${configId}`
  })
}

export const fetchDataMonitorDashboardApi = (
  params: MonitorQueryParams = {}
): Promise<IResponse<DataMonitorDashboardItem>> => {
  return request.get({ url: '/api/v1/data-monitor/dashboard', params })
}

export const fetchSlowSqlEventsApi = (
  params: MonitorQueryParams = {}
): Promise<IResponse<SlowSqlEventItem[]>> => {
  return request.get({ url: '/api/v1/data-monitor/slow-sql', params })
}

export const fetchPoolRisksApi = (
  params: MonitorQueryParams = {}
): Promise<IResponse<PoolRiskItem[]>> => {
  return request.get({ url: '/api/v1/data-monitor/pools/risks', params })
}

export const fetchLogQualityIssuesApi = (
  params: MonitorQueryParams = {}
): Promise<IResponse<LogQualityIssueItem[]>> => {
  return request.get({ url: '/api/v1/data-monitor/log-quality/issues', params })
}

export const ignoreSlowSqlApi = (
  id: string | number,
  payload: MonitorActionPayload
): Promise<IResponse<SlowSqlEventItem>> => {
  return request.post({ url: `/api/v1/data-monitor/slow-sql/${id}/ignore`, data: payload })
}

export const confirmSlowSqlApi = (
  id: string | number,
  payload: SlowSqlConfirmPayload
): Promise<IResponse<SlowSqlEventItem>> => {
  return request.post({ url: `/api/v1/data-monitor/slow-sql/${id}/confirm`, data: payload })
}

export const ignoreLogQualityIssueApi = (
  id: string | number,
  payload: MonitorActionPayload
): Promise<IResponse<LogQualityIssueItem>> => {
  return request.post({
    url: `/api/v1/data-monitor/log-quality/issues/${id}/ignore`,
    data: payload
  })
}
