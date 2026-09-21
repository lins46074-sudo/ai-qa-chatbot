import { del, get, post, put } from '@/utils/request'
import type {
  AccessLogVO,
  AdminUserVO,
  DocumentVO,
  PageResult,
  RegisterDTO,
  ResetPasswordDTO,
  StatsOverviewVO,
  UserStatusDTO
} from './types'

export interface AdminUserPageParams {
  current?: number
  size?: number
  keyword?: string
  role?: string
  status?: number
}
export interface AdminDocPageParams {
  current?: number
  size?: number
  keyword?: string
}

/** 用户分页（含文档数/会话数联查） */
export const pageUsers = (params: AdminUserPageParams) =>
  get<PageResult<AdminUserVO>>('/admin/user/page', params)

/** 新增用户 */
export const createUser = (d: RegisterDTO) => post<void>('/admin/user', d)

/** 启用/禁用用户（禁用即踢下线） */
export const setUserStatus = (id: number, status: number) => {
  const d: UserStatusDTO = { status }
  return put<void>(`/admin/user/${id}/status`, d)
}

/** 重置用户密码 */
export const resetPassword = (id: number, newPassword: string) => {
  const d: ResetPasswordDTO = { newPassword }
  return put<void>(`/admin/user/${id}/password`, d)
}

/** 删除用户（级联清理其文档/会话/文件） */
export const deleteUser = (id: number) => del<void>(`/admin/user/${id}`)

/** 全库文档分页（资源统计） */
export const pageAdminDocs = (params: AdminDocPageParams) =>
  get<PageResult<DocumentVO>>('/admin/doc/page', params)

/** 强删任意文档 */
export const deleteAdminDoc = (id: number) => del<void>(`/admin/doc/${id}`)

/** 数据看板汇总（卡片 + 近7日趋势 + Top10 文档） */
export const overview = () => get<StatsOverviewVO>('/admin/overview')

/** 访问日志分页 */
export const pageAccessLogs = (params: { current?: number; size?: number }) =>
  get<PageResult<AccessLogVO>>('/admin/access/page', params)
