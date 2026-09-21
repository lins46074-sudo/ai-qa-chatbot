import { del, get, post, put } from '@/utils/request'
import type { MessageVO, PageResult, SessionCreateDTO, SessionRenameDTO, SessionVO } from './types'

/** 会话分页（按创建时间倒序） */
export const pageSessions = (params: { current?: number; size?: number }) =>
  get<PageResult<SessionVO>>('/chat/session/page', params)

/** 新建会话 */
export const createSession = (d: SessionCreateDTO) => post<SessionVO>('/chat/session', d)

/** 重命名会话 */
export const renameSession = (id: number, d: SessionRenameDTO) => put<SessionVO>(`/chat/session/${id}`, d)

/** 删除会话（连同历史消息与上下文缓存） */
export const deleteSession = (id: number) => del<void>(`/chat/session/${id}`)

/** 会话内历史消息分页（按 id 正序翻页，页码越大越新） */
export const pageMessages = (sessionId: number, params: { current?: number; size?: number }) =>
  get<PageResult<MessageVO>>(`/chat/session/${sessionId}/message/page`, params)

// 注意：流式问答 POST /chat/stream 不走 axios，
// 请在组件中调用 @/utils/sse 的 postStream('/chat/stream', body, handlers)
