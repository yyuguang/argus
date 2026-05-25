export interface BasePageParams {
  pageNo?: number
  pageIndex?: number
  currentPage?: number
  pageSize?: number
  [key: string]: unknown
}

export interface PageResult<T> {
  records?: T[]
  pageNo?: number
  pageSize?: number
  total?: number
}

export interface TableListResponse<T> {
  list: T[]
  total: number
  pageNo?: number
  pageSize?: number
}

export const toPageRequest = <T extends BasePageParams>(params?: T) => {
  const request: Record<string, unknown> = { ...(params || {}) }
  const pageNo = params?.pageNo ?? params?.pageIndex ?? params?.currentPage

  delete request.pageIndex
  delete request.currentPage

  if (pageNo !== undefined) {
    request.pageNo = pageNo
  }

  return request
}

export const toTableListResponse = <T>(
  response: IResponse<PageResult<T>>
): IResponse<TableListResponse<T>> => {
  const page = response.data || {}

  return {
    ...response,
    data: {
      list: page.records || [],
      total: page.total || 0,
      pageNo: page.pageNo,
      pageSize: page.pageSize
    }
  }
}
