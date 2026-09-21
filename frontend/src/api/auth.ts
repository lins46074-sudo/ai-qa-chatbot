import { get, post, put } from '@/utils/request'
import type { LoginDTO, LoginVO, RegisterDTO, UpdatePasswordDTO, UpdateProfileDTO, UserVO } from './types'

/** 登录：返回 token + 用户信息 */
export const login = (d: LoginDTO) => post<LoginVO>('/auth/login', d)

/** 注册 */
export const register = (d: RegisterDTO) => post<void>('/auth/register', d)

/** 注销登录（删除 Redis 登录态） */
export const logout = () => post<void>('/auth/logout')

/** 当前登录用户信息 */
export const getUserInfo = () => get<UserVO>('/user/info')

/** 修改个人资料 */
export const updateProfile = (d: UpdateProfileDTO) => put<UserVO>('/user/profile', d)

/** 修改密码 */
export const updatePassword = (d: UpdatePasswordDTO) => put<void>('/user/password', d)
