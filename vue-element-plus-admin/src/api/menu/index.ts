import request from '@/axios'
import type { MenuItem, MenuListResponse, MenuOrderItem } from './types'

const MENU_API_BASE = '/api/v1/admin/menus'

const toMenuPayload = (data: MenuItem) => {
  const meta = data.meta || {}

  return {
    type: data.type,
    parentId: data.parentId,
    path: data.path,
    component: data.component,
    name: data.name,
    redirect: data.redirect,
    status: data.status,
    sortOrder: data.sortOrder,
    meta: {
      ...meta,
      title: meta.title || data.title || data.name
    },
    permissionList: data.permissionList || []
  }
}

export const getMenuListApi = (): Promise<IResponse<MenuListResponse>> => {
  return request.get({ url: `${MENU_API_BASE}/tree` })
}

export const saveMenuApi = (data: MenuItem) => {
  const menuId = data.id
  const payload = toMenuPayload(data)

  return menuId
    ? request.put({ url: `${MENU_API_BASE}/${menuId}`, data: payload })
    : request.post({ url: MENU_API_BASE, data: payload })
}

export const deleteMenuApi = (ids: Array<string | number>) => {
  return request.delete({ url: MENU_API_BASE, data: { ids } })
}

export const updateMenuStatusApi = (ids: Array<string | number>, status: number) => {
  return request.patch({ url: `${MENU_API_BASE}/status`, data: { ids, status } })
}

export const updateMenuOrderApi = (items: MenuOrderItem[]) => {
  return request.patch({ url: `${MENU_API_BASE}/order`, data: { items } })
}
