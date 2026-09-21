<template>
  <!-- 主布局：左侧深蓝菜单 + 右侧顶栏与内容区 -->
  <el-container class="app-layout">
    <el-aside width="220px" class="app-aside">
      <div class="logo">
        <el-icon :size="22" color="#fff"><ChatLineRound /></el-icon>
        <span>私有文档 AI 问答</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="transparent"
        text-color="#c9d6e6"
        active-text-color="#ffffff"
        class="aside-menu"
        router
      >
        <el-menu-item
          v-for="item in menus"
          :key="item.path"
          :index="item.path"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-title">{{ currentTitle }}</div>
        <el-dropdown @command="onCommand">
          <div class="user-chip">
            <el-avatar :size="30" :src="user?.avatar || ''">
              {{ (user?.nickname || user?.username || 'U').slice(0, 1) }}
            </el-avatar>
            <span class="nick">{{ user?.nickname || user?.username }}</span>
            <el-tag size="small" :type="user?.role === 'ADMIN' ? 'danger' : 'primary'" effect="dark" class="role-tag">
              {{ user?.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { homePathOf } from '@/router'

interface MenuItem {
  path: string
  title: string
  icon: string
  roles: string[]
}

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const user = computed(() => store.user)

/** 菜单按角色过滤：USER 见用户端菜单，ADMIN 见管理端菜单 */
const allMenus: MenuItem[] = [
  { path: '/workspace', title: '问答工作台', icon: 'ChatDotRound', roles: ['USER'] },
  { path: '/docs', title: '我的文档', icon: 'FolderOpened', roles: ['USER'] },
  { path: '/admin/overview', title: '数据看板', icon: 'DataAnalysis', roles: ['ADMIN'] },
  { path: '/admin/users', title: '用户管理', icon: 'User', roles: ['ADMIN'] },
  { path: '/admin/docs', title: '文档统计', icon: 'Document', roles: ['ADMIN'] }
]
const menus = computed(() => allMenus.filter((m) => m.roles.includes(store.role || '')))

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => (route.meta.title as string) || '')

onMounted(() => {
  // 页面刷新后若 token 存在但用户信息缺失，重新拉取
  if (store.isLogin && !store.user) {
    store.fetchInfo().catch(() => store.clear())
  }
})

async function onCommand(cmd: string) {
  if (cmd !== 'logout') return
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await store.logout()
  router.push('/login')
}

// 无菜单权限访问（如角色为空）时跳转兜底
void homePathOf
</script>

<style scoped lang="scss">
.app-layout {
  height: 100vh;
}
.app-aside {
  background: var(--aidoc-sidebar-bg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}
.aside-menu {
  flex: 1;
  border-right: none;
  padding: 8px;
  --el-menu-item-height: 46px;
  .el-menu-item {
    border-radius: 6px;
    margin-bottom: 4px;
    &.is-active {
      background: var(--aidoc-sidebar-active);
    }
  }
}
.app-header {
  background: var(--aidoc-header-bg);
  box-shadow: 0 1px 4px rgba(18, 44, 82, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
}
.header-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--aidoc-text-main);
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.nick {
  font-size: 14px;
  color: var(--aidoc-text-main);
}
.role-tag {
  border: none;
}
.app-main {
  background: var(--aidoc-bg);
  padding: 16px;
  overflow-y: auto;
}
</style>
