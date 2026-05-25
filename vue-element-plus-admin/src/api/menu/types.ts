export interface MenuMeta {
  title?: string
  icon?: string
  activeMenu?: string
  hidden?: boolean
  alwaysShow?: boolean
  noCache?: boolean
  breadcrumb?: boolean
  affix?: boolean
  noTagsView?: boolean
  canTo?: boolean
  permission?: string[]
}

export interface MenuPermissionItem {
  id?: string | number
  label: string
  value: string
  permissionCode: string
  status?: number
}

export interface MenuItem {
  id?: string | number
  type?: number
  parentId?: string | number | null
  path?: string
  component?: string
  redirect?: string | null
  name?: string
  status?: number
  sortOrder?: number
  title?: string
  meta?: MenuMeta
  permissionList?: MenuPermissionItem[]
  children?: MenuItem[]
}

export interface MenuListResponse {
  list: MenuItem[]
}

export interface MenuOrderItem {
  id: string | number
  sortOrder: number
}
