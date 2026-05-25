import request from '@/axios'
import { listScmConfigsApi, saveScmConfigApi } from '@/api/scm'
import type { ScmConfigItem } from '@/api/scm/types'
import { getProjectMappingsApi } from '@/api/scm'
import type { ProjectMappingItem } from '@/api/scm/types'
import type {
  AppLinkageFormModel,
  AppLinkageListItem,
  AppLinkageListResponse,
  AppLinkagePageParams,
  AppLinkageScmOption,
  ErrorAlertRouteMap,
  ErrorAlertRouteModel
} from './types'

const PROJECT_MAPPING_API_BASE = '/api/v1/registry/project-mappings'
const DEFAULT_SOURCE_ROOT = 'src/main/java'
const DEFAULT_BRANCH = 'main'
const ROUTE_SEVERITIES = ['P0', 'P1', 'P2', 'P3']

const providerSortWeight: Record<string, number> = {
  gitlab: 1,
  github: 2,
  gitee: 3
}

interface ParsedReviewConfig {
  trigger: Record<string, any>
  fileFilter: Record<string, any>
  scoring: Record<string, any>
  notification: {
    scoreAlertThreshold: number
    scoreAlertChannels: string[]
    platforms: Record<string, { enabled: boolean; webhook: string }>
    errorAlertRoutes: ErrorAlertRouteMap
    retry: {
      maxRetries: number
      timeoutSec: number
      backoffSeconds: number[]
    }
  }
}

const safeJsonParse = <T>(value?: string, fallback?: T): T => {
  if (!value) {
    return (fallback ?? ({} as T)) as T
  }
  try {
    return JSON.parse(value) as T
  } catch {
    return (fallback ?? ({} as T)) as T
  }
}

const parseStringArray = (value?: string) => {
  const parsed = safeJsonParse<unknown[]>(value, [])
  if (!Array.isArray(parsed)) {
    return []
  }
  return parsed.map((item) => String(item || '').trim()).filter(Boolean)
}

const createDefaultErrorAlertRoutes = (): ErrorAlertRouteMap => ({
  P0: { enabled: true, channel: 'critical', priority: 'urgent' },
  P1: { enabled: true, channel: 'critical', priority: 'urgent' },
  P2: { enabled: true, channel: 'default', priority: 'normal' },
  P3: { enabled: false, channel: 'default', priority: 'low' }
})

const cloneErrorAlertRoutes = (routes?: ErrorAlertRouteMap): ErrorAlertRouteMap => {
  const defaults = createDefaultErrorAlertRoutes()
  const normalized: ErrorAlertRouteMap = {}

  ROUTE_SEVERITIES.forEach((severity) => {
    const source = routes?.[severity] || defaults[severity]
    normalized[severity] = {
      enabled: source.enabled !== false,
      channel: source.channel || defaults[severity].channel,
      priority: source.priority || defaults[severity].priority
    }
  })

  return normalized
}

const parseReviewConfigModel = (raw?: string): ParsedReviewConfig => {
  const parsed = safeJsonParse<Record<string, any>>(raw, {})
  const notification = parsed.notification || {}
  const platforms = notification.platforms || {}

  return {
    trigger: parsed.trigger || {
      enabled: true,
      eventTypes: ['opened', 'update', 'synchronize', 'reopened'],
      branchMode: 'TARGET_ONLY',
      targetBranches: ['test'],
      sourceBranches: []
    },
    fileFilter: parsed.fileFilter || {
      maxReviewFiles: 15,
      maxDiffLinesPerFile: 500,
      maxTotalDiffLines: 3000,
      excludeFilePatterns: [],
      binaryExtensions: []
    },
    scoring: parsed.scoring || {
      blockThreshold: 60,
      aiWeight: 0.6,
      ruleWeight: 0.4
    },
    notification: {
      scoreAlertThreshold: Number(notification.scoreAlertThreshold || 60),
      scoreAlertChannels: Array.isArray(notification.scoreAlertChannels)
        ? notification.scoreAlertChannels
        : [],
      platforms: {
        wechat: {
          enabled: platforms.wechat?.enabled === true,
          webhook: String(platforms.wechat?.webhook || '')
        },
        feishu: {
          enabled: platforms.feishu?.enabled === true,
          webhook: String(platforms.feishu?.webhook || '')
        },
        dingtalk: {
          enabled: platforms.dingtalk?.enabled === true,
          webhook: String(platforms.dingtalk?.webhook || '')
        }
      },
      errorAlertRoutes: cloneErrorAlertRoutes(notification.errorAlertRoutes),
      retry: {
        maxRetries: Number(notification.retry?.maxRetries || 3),
        timeoutSec: Number(notification.retry?.timeoutSec || 600),
        backoffSeconds: Array.isArray(notification.retry?.backoffSeconds)
          ? notification.retry.backoffSeconds
          : [30, 120, 300]
      }
    }
  }
}

const toErrorAlertRoutes = (reviewConfig?: string) => {
  return cloneErrorAlertRoutes(parseReviewConfigModel(reviewConfig).notification.errorAlertRoutes)
}

const composeRepo = (
  config?: Pick<ScmConfigItem, 'projectName' | 'repoOwner' | 'repoName'> | null
) => {
  if (!config) {
    return '-'
  }
  if (config.projectName) {
    return config.projectName
  }
  if (config.repoOwner && config.repoName) {
    return `${config.repoOwner}/${config.repoName}`
  }
  return config.repoName || config.repoOwner || '-'
}

const normalizeScmOptions = (configs: ScmConfigItem[]): AppLinkageScmOption[] => {
  return configs
    .filter(
      (item) => item.id !== undefined && item.projectId !== null && item.projectId !== undefined
    )
    .map((item) => {
      const moduleSourceRoots = parseStringArray(item.moduleSourceRoots)
      const basePackages = parseStringArray(item.basePackages)

      return {
        id: item.id as string | number,
        scmProvider: item.scmProvider || '',
        projectId: Number(item.projectId),
        projectName: composeRepo(item),
        repoOwner: item.repoOwner || '',
        repoName: item.repoName || '',
        enabled: item.enabled !== false,
        reviewConfig: item.reviewConfig,
        defaultSourceRoot:
          moduleSourceRoots.length === 1 ? moduleSourceRoots[0] : DEFAULT_SOURCE_ROOT,
        defaultBasePackage: basePackages[0] || '',
        errorAlertRoutes: toErrorAlertRoutes(item.reviewConfig)
      }
    })
    .sort((a, b) => {
      const providerDiff =
        (providerSortWeight[a.scmProvider] || 99) - (providerSortWeight[b.scmProvider] || 99)
      if (providerDiff !== 0) {
        return providerDiff
      }
      return a.projectName.localeCompare(b.projectName, 'zh-Hans-CN')
    })
}

const buildHealth = (mapping: ProjectMappingItem, scmOption?: AppLinkageScmOption) => {
  if (!scmOption) {
    return {
      healthStatus: 'SCM_MISSING' as const,
      healthLabel: '仓库缺失',
      healthDescription: '映射关联的 SCM 配置已不存在或缺少项目 ID'
    }
  }
  if (!mapping.sourceRoot || !mapping.basePackage || !mapping.defaultBranch) {
    return {
      healthStatus: 'INCOMPLETE' as const,
      healthLabel: '待补齐',
      healthDescription: '源码根、基础包或默认分支未完整配置'
    }
  }
  return {
    healthStatus: 'READY' as const,
    healthLabel: '可定位',
    healthDescription: '错误日志可联动到仓库源码与告警路由'
  }
}

const buildAppLinkageList = (
  configs: ScmConfigItem[],
  mappings: ProjectMappingItem[]
): AppLinkageListItem[] => {
  const scmOptions = normalizeScmOptions(configs)
  const scmOptionMap = new Map<string, AppLinkageScmOption>(
    scmOptions.map((item) => [`${item.scmProvider}:${item.projectId}`, item] as const)
  )

  return mappings.map((mapping) => {
    const scmKey = `${mapping.scmProvider || ''}:${mapping.scmProjectId || ''}`
    const scmOption = scmOptionMap.get(scmKey)
    const health = buildHealth(mapping, scmOption)

    return {
      id: mapping.id,
      appName: mapping.appName || '',
      scmConfigId: scmOption?.id,
      scmProvider: mapping.scmProvider || scmOption?.scmProvider || '',
      scmProjectId: mapping.scmProjectId,
      projectName: scmOption?.projectName || '-',
      repoOwner: scmOption?.repoOwner || '',
      repoName: scmOption?.repoName || '',
      scmEnabled: scmOption?.enabled !== false,
      sourceRoot: mapping.sourceRoot || '',
      basePackage: mapping.basePackage || '',
      defaultBranch: mapping.defaultBranch || '',
      healthStatus: health.healthStatus,
      healthLabel: health.healthLabel,
      healthDescription: health.healthDescription,
      errorAlertRoutes: scmOption?.errorAlertRoutes || createDefaultErrorAlertRoutes(),
      createTime: mapping.createTime,
      updateTime: mapping.updateTime
    }
  })
}

const keywordMatch = (row: AppLinkageListItem, keyword?: string) => {
  if (!keyword) {
    return true
  }
  const normalized = keyword.trim().toLowerCase()
  const fields = [
    row.appName,
    row.projectName,
    row.repoOwner,
    row.repoName,
    row.sourceRoot,
    row.basePackage,
    row.defaultBranch
  ]
  return fields.some((item) =>
    String(item || '')
      .toLowerCase()
      .includes(normalized)
  )
}

const filterAppLinkageList = (rows: AppLinkageListItem[], params?: AppLinkagePageParams) => {
  return rows.filter((row) => {
    if (params?.scmProvider && row.scmProvider !== params.scmProvider) {
      return false
    }
    if (
      params?.scmConfigId !== undefined &&
      params?.scmConfigId !== null &&
      String(row.scmConfigId || '') !== String(params.scmConfigId)
    ) {
      return false
    }
    if (params?.healthStatus && row.healthStatus !== params.healthStatus) {
      return false
    }
    return keywordMatch(row, params?.keyword)
  })
}

const toPagedResponse = (
  rows: AppLinkageListItem[],
  params?: AppLinkagePageParams
): IResponse<AppLinkageListResponse> => {
  const pageNo = Number(params?.pageNo ?? params?.pageIndex ?? params?.currentPage ?? 1) || 1
  const pageSize = Number(params?.pageSize ?? 10) || 10
  const start = Math.max(pageNo - 1, 0) * pageSize
  const list = rows.slice(start, start + pageSize)

  return {
    code: 200,
    data: {
      list,
      total: rows.length,
      pageNo,
      pageSize
    },
    message: 'success'
  }
}

const buildProjectMappingPayload = (
  form: AppLinkageFormModel,
  scmOption: AppLinkageScmOption
): ProjectMappingItem => ({
  appName: form.appName.trim(),
  scmProvider: scmOption.scmProvider,
  scmProjectId: scmOption.projectId,
  sourceRoot: form.sourceRoot.trim(),
  basePackage: form.basePackage.trim() || undefined,
  defaultBranch: form.defaultBranch.trim()
})

const buildReviewConfigWithRoutes = (scmConfig: ScmConfigItem, routes: ErrorAlertRouteMap) => {
  const reviewConfig = parseReviewConfigModel(scmConfig.reviewConfig)
  reviewConfig.notification.errorAlertRoutes = cloneErrorAlertRoutes(routes)

  // SCM 查询接口会脱敏 webhook，这里统一清空后交给后端保留原值，避免把掩码串写回数据库。
  Object.values(reviewConfig.notification.platforms || {}).forEach((platform) => {
    platform.webhook = ''
  })

  return JSON.stringify(reviewConfig)
}

export const getAppLinkagePageApi = async (
  params?: AppLinkagePageParams
): Promise<IResponse<AppLinkageListResponse>> => {
  const [scmRes, mappingRes] = await Promise.all([listScmConfigsApi(), getProjectMappingsApi()])
  const rows = buildAppLinkageList(scmRes.data || [], mappingRes.data || [])
  const filtered = filterAppLinkageList(rows, params).sort((a, b) =>
    String(a.appName || '').localeCompare(String(b.appName || ''), 'zh-Hans-CN')
  )
  return toPagedResponse(filtered, params)
}

export const getAppLinkageScmOptionsApi = async (): Promise<IResponse<AppLinkageScmOption[]>> => {
  const res = await listScmConfigsApi()
  return {
    ...res,
    data: normalizeScmOptions(res.data || [])
  }
}

export const buildEmptyErrorAlertRoutes = () => createDefaultErrorAlertRoutes()

export const buildEmptyAppLinkageForm = (): AppLinkageFormModel => ({
  appName: '',
  sourceRoot: DEFAULT_SOURCE_ROOT,
  basePackage: '',
  defaultBranch: DEFAULT_BRANCH,
  errorAlertRoutes: createDefaultErrorAlertRoutes()
})

export const toAppLinkageForm = (item?: AppLinkageListItem): AppLinkageFormModel => ({
  id: item?.id,
  scmConfigId: item?.scmConfigId,
  appName: item?.appName || '',
  sourceRoot: item?.sourceRoot || DEFAULT_SOURCE_ROOT,
  basePackage: item?.basePackage || '',
  defaultBranch: item?.defaultBranch || DEFAULT_BRANCH,
  errorAlertRoutes: cloneErrorAlertRoutes(item?.errorAlertRoutes)
})

export const saveAppLinkageApi = async (form: AppLinkageFormModel) => {
  const scmOptionsRes = await getAppLinkageScmOptionsApi()
  const targetScm = (scmOptionsRes.data || []).find(
    (item) => String(item.id) === String(form.scmConfigId || '')
  )
  if (!targetScm) {
    throw new Error('请选择有效的 SCM 仓库')
  }

  const payload = buildProjectMappingPayload(form, targetScm)
  const mappingResponse = form.id
    ? await request.put<ProjectMappingItem>({
        url: `${PROJECT_MAPPING_API_BASE}/${form.id}`,
        data: payload
      })
    : await request.post<ProjectMappingItem>({
        url: PROJECT_MAPPING_API_BASE,
        data: payload
      })

  await saveScmConfigApi({
    id: targetScm.id,
    scmProvider: targetScm.scmProvider,
    projectId: targetScm.projectId,
    projectName: targetScm.projectName,
    repoOwner: targetScm.repoOwner,
    repoName: targetScm.repoName,
    enabled: targetScm.enabled,
    reviewConfig: buildReviewConfigWithRoutes(
      {
        id: targetScm.id,
        scmProvider: targetScm.scmProvider,
        projectId: targetScm.projectId,
        projectName: targetScm.projectName,
        repoOwner: targetScm.repoOwner,
        repoName: targetScm.repoName,
        enabled: targetScm.enabled,
        reviewConfig: targetScm.reviewConfig
      },
      form.errorAlertRoutes
    )
  })

  return mappingResponse
}

export const deleteAppLinkageApi = (id: string | number) => {
  return request.delete({
    url: `${PROJECT_MAPPING_API_BASE}/${id}`
  })
}

export const routeEnabledCount = (routes?: ErrorAlertRouteMap) => {
  return ROUTE_SEVERITIES.filter((severity) => routes?.[severity]?.enabled !== false).length
}

export const routeSummaryText = (routes?: ErrorAlertRouteMap) => {
  const normalized = cloneErrorAlertRoutes(routes)
  return ROUTE_SEVERITIES.map((severity) => {
    const config: ErrorAlertRouteModel = normalized[severity]
    return `${severity}:${config.enabled ? `${config.channel}/${config.priority}` : '关闭'}`
  }).join(' | ')
}
