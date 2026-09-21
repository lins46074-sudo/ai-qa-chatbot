import { del, get, post } from '@/utils/request'
import type { ChunkMergeDTO, ChunkStatusVO, DocumentVO, PageResult } from './types'

export interface DocPageParams {
  current?: number
  size?: number
  keyword?: string
  fileType?: string
  status?: number
}

/** 我的文档分页 */
export const pageDocs = (params: DocPageParams) => get<PageResult<DocumentVO>>('/doc/page', params)

/** 文档详情 */
export const getDoc = (id: number) => get<DocumentVO>(`/doc/${id}`)

/** 删除文档（级联删除会话/消息/文件/缓存） */
export const deleteDoc = (id: number) => del<void>(`/doc/${id}`)

/** 小文件直接上传（≤2MB 走直传，无需分片） */
export const uploadDoc = (file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  return post<DocumentVO>('/doc/upload', fd as unknown as object)
}

/** 分片上传前查询：返回已上传的分片 index（用于秒传/断点续传） */
export const chunkStatus = (identifier: string) =>
  get<ChunkStatusVO>('/doc/chunk/status', { identifier })

/** 上传单个分片 */
export const uploadChunk = (fd: FormData) => post<void>('/doc/chunk/upload', fd as unknown as object)

/** 合并分片并触发解析 */
export const mergeChunks = (d: ChunkMergeDTO) => post<DocumentVO>('/doc/chunk/merge', d)
