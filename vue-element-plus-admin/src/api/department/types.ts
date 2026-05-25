import type { BasePageParams, TableListResponse } from '@/api/common/page'

export interface DepartmentItem {
  id: string
  parentId?: string | null
  departmentName: string
  status?: number
  sortOrder?: number
  createTime?: string
  remark?: string
  children?: DepartmentItem[]
}

export interface DepartmentListResponse {
  list: DepartmentItem[]
}

export interface DepartmentPageParams extends BasePageParams {
  departmentName?: string
  status?: number
}

export type DepartmentTableResponse = TableListResponse<DepartmentItem>
