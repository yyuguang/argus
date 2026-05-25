import request from '@/axios'
import { toPageRequest, toTableListResponse } from '@/api/common/page'
import type { PageResult } from '@/api/common/page'
import type {
  DepartmentItem,
  DepartmentListResponse,
  DepartmentPageParams,
  DepartmentTableResponse
} from './types'

const DEPARTMENT_API_BASE = '/api/v1/admin/departments'

const toDepartmentPayload = (data: any) => {
  return {
    parentId: data.parentId ?? null,
    departmentName: data.departmentName,
    status: data.status,
    remark: data.remark,
    sortOrder: data.sortOrder
  }
}

export const getDepartmentApi = (): Promise<IResponse<DepartmentListResponse>> => {
  return request.get({ url: `${DEPARTMENT_API_BASE}/tree` })
}

export const saveDepartmentApi = (data: any) => {
  const updating = data?.id !== undefined
  const payload = toDepartmentPayload(data)

  return updating
    ? request.put({ url: `${DEPARTMENT_API_BASE}/${data.id}`, data: payload })
    : request.post({ url: DEPARTMENT_API_BASE, data: payload })
}

export const deleteDepartmentApi = (ids: string[] | number[]) => {
  return request.delete({ url: DEPARTMENT_API_BASE, data: { ids } })
}

export const getDepartmentTableApi = (
  params: DepartmentPageParams
): Promise<IResponse<DepartmentTableResponse>> => {
  return request
    .post<PageResult<DepartmentItem>>({
      url: `${DEPARTMENT_API_BASE}/page`,
      data: toPageRequest(params)
    })
    .then(toTableListResponse<DepartmentItem>)
}
