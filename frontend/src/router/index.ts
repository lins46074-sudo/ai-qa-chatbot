import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { storage } from '@/utils/storage'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

/**
 * 路由表：布局路由下挂载用户端与管理端页面（按 meta.roles 区分权限）
 * 说明：各页面组件由并行开发小组按既定路径创建，此处路径与之锁定一致。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/workspace',
    children: [
      // ---------- 用户端 ----------
      {
        path: 'workspace',
        name: 'Workspace',
        component: () => import('@/views/user/Workspace.vue'),
        meta: { title: '问答工作台', roles: ['USER'] }
      },
      {
        path: 'docs',
        name: 'Docs',
        component: () => import('@/views/user/Docs.vue'),
        meta: { title: '我的文档', roles: ['USER'] }
      },
      // ---------- 管理端 ----------
      {
        path: 'admin/overview',
        name: 'AdminOverview',
        component: () => import('@/views/admin/Overview.vue'),
        meta: { title: '数据看板', roles: ['ADMIN'] }
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/docs',
        name: 'AdminDocs',
        component: () => import('@/views/admin/Docs.vue'),
        meta: { title: '文档统计', roles: ['ADMIN'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { public: true, title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 按角色返回首页路径 */
export function homePathOf(role: string): string {
  return role === 'ADMIN' ? '/admin/overview' : '/workspace'
}

// 全局前置守卫：登录态 + 角色权限
router.beforeEach((to) => {
  const store = useUserStore()
  const hasToken = store.isLogin || !!storage.getToken()

  if (!hasToken) {
    // 未登录：仅放行公开页
    if (to.meta.public) return true
    return { path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }

  // 已登录访问登录页 → 跳角色首页
  if (to.path === '/login') return homePathOf(store.role || 'USER')

  // 角色权限校验
  const roles = to.meta.roles as string[] | undefined
  if (roles && roles.length && store.role && !roles.includes(store.role)) {
    ElMessage.warning('无权访问该页面')
    return homePathOf(store.role)
  }
  return true
})

// 页面标题
router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title as string} · 私有文档 AI 问答`
    : '私有文档 AI 问答'
})

export default router
