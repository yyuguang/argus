import request from '@/axios'
import { toPageRequest, toTableListResponse } from '@/api/common/page'
import type { AxiosResponse } from '@/axios/types'
import type { PageResult } from '@/api/common/page'
import type { UserImportResult, UserItem, UserListResponse, UserPageParams } from './types'

const USER_API_BASE = '/api/v1/admin/users'

const toUserPayload = (data: UserItem | Record<string, any>) => {
  const payload = { ...data }

  delete payload.id
  delete payload.createTime
  delete payload.roleNames

  payload.role = Array.isArray(payload.role)
    ? payload.role.map((item: unknown) => String(item))
    : []
  payload.department = payload.department?.id ? { id: payload.department.id } : undefined

  return payload
}

export const getUserPageApi = (params: UserPageParams): Promise<IResponse<UserListResponse>> => {
  return request
    .post<PageResult<UserItem>>({
      url: `${USER_API_BASE}/page`,
      data: toPageRequest(params)
    })
    .then(toTableListResponse<UserItem>)
}

export const deleteUserApi = (ids: string[] | number[]) => {
  return request.delete({ url: USER_API_BASE, data: { ids } })
}

export const saveUserApi = (data: UserItem | Record<string, any>) => {
  const userId = data?.id
  const payload = toUserPayload(data)

  return userId
    ? request.put({ url: `${USER_API_BASE}/${userId}`, data: payload })
    : request.post({ url: USER_API_BASE, data: payload })
}

export const resetUserPasswordApi = (id: string | number, password: string) => {
  return request.patch({ url: `${USER_API_BASE}/${id}/password`, data: { password } })
}

export const updateUserStatusApi = (
  ids: Array<string | number>,
  status: number,
  reason?: string
) => {
  return request.patch({ url: `${USER_API_BASE}/status`, data: { ids, status, reason } })
}

export const importUsersApi = (file: File): Promise<IResponse<UserImportResult>> => {
  return request.post({
    url: `${USER_API_BASE}/imports`,
    data: { file },
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const exportUsersApi = (): Promise<AxiosResponse<Blob>> => {
  return request.get({
    url: `${USER_API_BASE}/exports`,
    responseType: 'blob'
  }) as unknown as Promise<AxiosResponse<Blob>>
}
