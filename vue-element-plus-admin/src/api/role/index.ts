import request from '@/axios'
import { toPageRequest, toTableListResponse } from '@/api/common/page'
import type { PageResult } from '@/api/common/page'
import type { RoleItem, RoleListResponse, RolePageParams } from './types'

const ROLE_API_BASE = '/api/v1/admin/roles'

const toRoleMenuGrantPayload = (menu: RoleItem['menu']) => {
  return (menu || []).map((item) => ({
    id: item.id,
    meta: {
      permission: item.meta?.permission || []
    }
  }))
}

const toRolePayload = (data: RoleItem) => {
  return {
    roleCode: data.roleCode,
    roleName: data.roleName,
    status: data.status,
    remark: data.remark,
    menu: toRoleMenuGrantPayload(data.menu)
  }
}

export const getRoleListApi = (
  params: RolePageParams = { pageNo: 1, pageSize: 200 }
): Promise<IResponse<RoleListResponse>> => {
  return request
    .post<PageResult<RoleItem>>({
      url: `${ROLE_API_BASE}/page`,
      data: toPageRequest(params)
    })
    .then(toTableListResponse<RoleItem>)
}

export const saveRoleApi = (data: RoleItem) => {
  const roleId = data.id
  const payload = toRolePayload(data)

  return roleId
    ? request.put({ url: `${ROLE_API_BASE}/${roleId}`, data: payload })
    : request.post({ url: ROLE_API_BASE, data: payload })
}

export const deleteRoleApi = (ids: Array<string | number>) => {
  return request.delete({ url: ROLE_API_BASE, data: { ids } })
}

export const assignRoleMenusApi = (roleId: string | number, menu: RoleItem['menu']) => {
  return request.put({
    url: `${ROLE_API_BASE}/${roleId}/menus`,
    data: { menu: toRoleMenuGrantPayload(menu) }
  })
}
