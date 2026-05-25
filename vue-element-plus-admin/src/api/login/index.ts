import request from '@/axios'
import type { CurrentUserResponse, LoginResponse, UserLoginType } from './types'

interface RoleParams {
  roleName: string
}

const AUTH_API_BASE = '/api/v1/admin/auth'

export const loginApi = (data: UserLoginType): Promise<IResponse<LoginResponse>> => {
  return request.post({ url: `${AUTH_API_BASE}/sessions`, data })
}

export const loginOutApi = (): Promise<IResponse> => {
  return request.delete({ url: `${AUTH_API_BASE}/sessions/current` })
}

export const getCurrentUserApi = (): Promise<IResponse<CurrentUserResponse>> => {
  return request.get({ url: `${AUTH_API_BASE}/me` })
}

export const getAdminRoleApi = (
  params?: RoleParams
): Promise<IResponse<AppCustomRouteRecordRaw[]>> => {
  return request.get({ url: `${AUTH_API_BASE}/routers`, params })
}
