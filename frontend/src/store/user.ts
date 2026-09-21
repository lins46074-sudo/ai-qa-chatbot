import { defineStore } from 'pinia'
import { storage } from '@/utils/storage'
import { getUserInfo, login as apiLogin, logout as apiLogout } from '@/api/auth'
import type { LoginDTO, UserVO } from '@/api/types'

/**
 * 用户登录态 store：token 与用户信息持久化于 localStorage
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: storage.getToken(),
    user: storage.getUser<UserVO>()
  }),
  getters: {
    isLogin: (s) => !!s.token,
    role: (s) => s.user?.role || ''
  },
  actions: {
    /** 登录：成功后持久化，返回角色供路由跳转使用 */
    async login(dto: LoginDTO) {
      const data = await apiLogin(dto)
      this.token = data.token
      this.user = data.user
      storage.setToken(data.token)
      storage.setUser(data.user)
      return data.user.role
    },
    /** 拉取最新用户信息（刷新页面后恢复登录态） */
    async fetchInfo() {
      const u = await getUserInfo()
      this.user = u
      storage.setUser(u)
      return u
    },
    /** 注销：通知后端删除 Redis 登录态并清空本地 */
    async logout() {
      try {
        await apiLogout()
      } catch {
        // 后端不可达时也允许本地退出
      }
      this.clear()
    },
    /** 仅清理本地（如 401 被踢） */
    clear() {
      this.token = ''
      this.user = null
      storage.clearAuth()
    }
  }
})
