import type { BasePageParams, TableListResponse } from '@/api/common/page'
import type { MenuMeta } from '@/api/menu/types'

export interface RoleMenuGrant {
  id: string | number
  meta?: Pick<MenuMeta, 'permission'>
}

export interface RoleItem {
  id?: string | number
  roleCode?: string
  roleName: string
  status?: number
  createTime?: string
  remark?: string
  menu?: RoleMenuGrant[]
}

export interface RolePageParams extends BasePageParams {
  roleName?: string
  status?: number
}

export type RoleListResponse = TableListResponse<RoleItem>
