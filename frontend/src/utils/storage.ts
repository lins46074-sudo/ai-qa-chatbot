/**
 * localStorage 存取封装：token 与用户信息持久化
 */
const TOKEN_KEY = 'aidoc_token'
const USER_KEY = 'aidoc_user'

export const storage = {
  /** 读取 token，无则返回空串 */
  getToken(): string {
    return localStorage.getItem(TOKEN_KEY) || ''
  },
  setToken(token: string) {
    localStorage.setItem(TOKEN_KEY, token)
  },
  /** 读取用户信息（JSON 容错解析） */
  getUser<T = any>(): T | null {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as T
    } catch {
      return null
    }
  },
  setUser<T = any>(user: T) {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  },
  /** 清空登录态 */
  clearAuth() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }
}
