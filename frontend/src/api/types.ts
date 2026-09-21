/**
 * 与后端 VO / DTO 精确对应的 TypeScript 类型（字段 camelCase，与后端 JSON 一致）
 */

/** 后端统一返回体（axios 拦截器已解包，通常不直接使用） */
export interface ApiResult<T = any> {
  code: number
  message: string
  data: T
}

/** 分页返回体 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/* ---------------- 认证与用户 ---------------- */
export interface LoginDTO {
  username: string
  password: string
  /** 登录入口：USER 用户端 / ADMIN 管理端 */
  loginType?: 'USER' | 'ADMIN'
}
export interface RegisterDTO {
  username: string
  password: string
  nickname?: string
  email?: string
}
export interface UpdateProfileDTO {
  nickname?: string
  email?: string
  avatar?: string
}
export interface UpdatePasswordDTO {
  oldPassword: string
  newPassword: string
}

export interface UserVO {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  role: string
  status: number
  lastLoginTime: string | null
  createTime: string
}
export interface LoginVO {
  token: string
  tokenType: string
  expiresIn: number
  user: UserVO
}

/* ---------------- 文档 ---------------- */
export interface DocumentVO {
  id: number
  userId: number
  userName?: string
  fileName: string
  fileType: string
  fileSize: number
  pageCount: number
  charCount: number
  status: number // 0解析中 1就绪 2失败
  failReason: string
  createTime: string
}
export interface ChunkStatusVO {
  identifier: string
  uploaded: number[]
}
export interface ChunkMergeDTO {
  identifier: string
  fileName: string
  totalChunks: number
  fileSize: number
}

/* ---------------- 会话与消息 ---------------- */
export interface SessionCreateDTO {
  documentId: number
  title?: string
}
export interface SessionRenameDTO {
  title: string
}
export interface MessageSendDTO {
  documentId: number
  sessionId?: number | null
  question: string
}

export interface SessionVO {
  id: number
  userId: number
  documentId: number
  documentName: string
  title: string
  messageCount: number
  createTime: string
  updateTime: string
}
export interface MessageVO {
  id: number
  sessionId: number
  role: string // USER / ASSISTANT
  content: string
  /** 引用来源（仅 ASSISTANT 消息可能有值，用于历史消息回显答案依据） */
  sources: SourceVO[] | null
  createTime: string
}

/** 引用来源：答案依据的文档片段（对应后端 SourceVO） */
export interface SourceVO {
  /** 引用编号，与答案正文中的 [n] 标注一一对应 */
  refIndex: number
  /** 片段在原文档中的切片序号 */
  chunkIndex: number
  /** 相似度得分（0~1，越高越相关） */
  score: number
  /** 片段原文 */
  snippet: string
  /** 片段在文档规范化文本中的起始下标 */
  charStart: number
  /** 片段在文档规范化文本中的结束下标（不含） */
  charEnd: number
}

/* ---------------- 管理端 ---------------- */
export interface AdminUserVO {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  role: string
  status: number
  lastLoginTime: string | null
  createTime: string
  docCount: number
  sessionCount: number
}
export interface UserStatusDTO {
  status: number
}
export interface ResetPasswordDTO {
  newPassword: string
}

export interface TrendPointVO {
  date: string
  visitCount: number
  messageCount: number
}
export interface TopDocumentVO {
  documentId: number
  fileName: string
  userName: string
  fileSize: number
  charCount: number
  createTime: string
}
export interface AccessLogVO {
  id: number
  userId: number | null
  username: string
  ip: string
  path: string
  method: string
  httpStatus: number
  costMs: number
  createTime: string
}
export interface StatsOverviewVO {
  userCount: number
  documentCount: number
  sessionCount: number
  messageCount: number
  todayVisitCount: number
  todayMessageCount: number
  todayNewUserCount: number
  trend: TrendPointVO[]
  topDocuments: TopDocumentVO[]
}
