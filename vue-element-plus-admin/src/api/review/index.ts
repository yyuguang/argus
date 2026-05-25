import request from '@/axios'
import { toPageRequest, toTableListResponse } from '@/api/common/page'
import type { PageResult } from '@/api/common/page'
import type {
  ReviewTaskDetailResponse,
  ReviewTaskItem,
  ReviewTaskListResponse,
  ReviewTaskPageParams
} from './types'

const REVIEW_TASK_API_BASE = '/api/v1/review/tasks'

export const getReviewTaskPageApi = (
  params: ReviewTaskPageParams
): Promise<IResponse<ReviewTaskListResponse>> => {
  return request
    .post<PageResult<ReviewTaskItem>>({
      url: `${REVIEW_TASK_API_BASE}/page`,
      data: toPageRequest(params)
    })
    .then(toTableListResponse<ReviewTaskItem>)
}

export const getReviewTaskDetailApi = (
  taskId: string | number
): Promise<IResponse<ReviewTaskDetailResponse>> => {
  return request.get({ url: `${REVIEW_TASK_API_BASE}/${taskId}` })
}
