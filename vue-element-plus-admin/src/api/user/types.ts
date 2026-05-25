import type { BasePageParams, TableListResponse } from '@/api/common/page'

/**
 * 用户所属部门简要信息。
 */
export interface UserDepartmentOption {
  id?: string
  departmentName?: string
}

/**
 * 用户分页查询参数。
 */
export interface UserPageParams extends BasePageParams {
  id?: string
  username?: string
  account?: string
  status?: number
  roleId?: string
}

/**
 * 用户列表与表单模型。
 */
export interface UserItem {
  id?: string
  username: string
  account: string
  email?: string
  phone?: string
  password?: string
  status?: number
  createTime?: string
  role: string[]
  roleNames?: string[]
  department: UserDepartmentOption
}

export type UserListResponse = TableListResponse<UserItem>

export interface UserImportError {
  rowNumber: number
  reason: string
}

export interface UserImportResult {
  total: number
  success: number
  failed: number
  errors: UserImportError[]
}
