import axios from 'axios'
import { ElMessage } from 'element-plus'
import { storage } from './storage'

/**
 * axios 统一封装：
 *  - 请求拦截器自动携带 Authorization: Bearer <token>
 *  - 响应拦截器解包统一返回体 Result<T>，直接返回 data
 *  - 401 统一清理登录态并跳转登录页
 */
const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000
})

http.interceptors.request.use((config) => {
  const token = storage.getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    // 后端统一返回 { code, message, data }，HTTP 状态均为 200
    const body = resp.data as { code: number; message: string; data: any }
    if (body.code !== 200) {
      ElMessage.error(body.message || '请求失败')
      if (body.code === 401) {
        storage.clearAuth()
        window.location.href = '/login'
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (err) => {
    ElMessage.error(err?.message?.includes('timeout') ? '请求超时，请稍后重试' : '网络异常，请检查服务是否启动')
    return Promise.reject(err)
  }
)

/** 泛型请求方法：返回值已解包为后端 data */
export const get = <T = any>(url: string, params?: object): Promise<T> => http.get(url, { params }) as unknown as Promise<T>
export const post = <T = any>(url: string, data?: object): Promise<T> => http.post(url, data) as unknown as Promise<T>
export const put = <T = any>(url: string, data?: object): Promise<T> => http.put(url, data) as unknown as Promise<T>
export const del = <T = any>(url: string): Promise<T> => http.delete(url) as unknown as Promise<T>

export default http
