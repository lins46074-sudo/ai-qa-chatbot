<template>
  <!-- 登录/注册页：左侧品牌展示 + 右侧表单卡片（管理端 / 用户端双入口） -->
  <div class="login-page" :class="`theme-${loginType.toLowerCase()}`">
    <div class="bg-orb orb-1"></div>
    <div class="bg-orb orb-2"></div>
    <div class="bg-orb orb-3"></div>

    <div class="login-shell">
      <!-- 左侧品牌区 -->
      <aside class="brand-panel">
        <div class="brand-logo">
          <el-icon :size="32"><ChatLineRound /></el-icon>
        </div>
        <h1 class="brand-title">AI 文档智能问答助手</h1>
        <p class="brand-desc">上传私有文档 · 基于大模型的智能问答 · 企业级知识库</p>

        <ul class="brand-features">
          <li><el-icon><UploadFilled /></el-icon><span>多格式文档一键上传解析</span></li>
          <li><el-icon><MagicStick /></el-icon><span>基于 RAG 的精准问答</span></li>
          <li><el-icon><DataAnalysis /></el-icon><span>知识库与对话记录管理</span></li>
        </ul>

        <div class="brand-footer">© 2026 AI Doc QA · 企业知识库平台</div>
      </aside>

      <!-- 右侧表单区 -->
      <section class="form-panel">
        <!-- 入口切换：用户端 / 管理端 -->
        <div class="entry-switch">
          <button
            class="entry-item"
            :class="{ active: loginType === 'USER' }"
            type="button"
            @click="switchType('USER')"
          >
            <el-icon><User /></el-icon>
            <span>用户登录</span>
          </button>
          <button
            class="entry-item"
            :class="{ active: loginType === 'ADMIN' }"
            type="button"
            @click="switchType('ADMIN')"
          >
            <el-icon><Setting /></el-icon>
            <span>管理员登录</span>
          </button>
          <span class="entry-slider" :class="{ right: loginType === 'ADMIN' }"></span>
        </div>

        <div class="form-head">
          <h2>{{ loginType === 'ADMIN' ? '管理员登录' : '欢迎回来' }}</h2>
          <p>
            {{
              loginType === 'ADMIN'
                ? '请使用管理员账号登录后台管理系统'
                : '登录后即可开始你的文档问答之旅'
            }}
          </p>
        </div>

        <el-tabs v-model="tab" stretch class="auth-tabs">
          <!-- 登录 -->
          <el-tab-pane label="登录" name="login">
            <el-form
              ref="loginRef"
              :model="loginForm"
              :rules="loginRules"
              label-position="top"
              @submit.prevent
            >
              <el-form-item label="用户名" prop="username">
                <el-input
                  v-model="loginForm.username"
                  :placeholder="loginType === 'ADMIN' ? '请输入管理员账号' : '请输入用户名'"
                  :prefix-icon="User"
                  size="large"
                  clearable
                />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  show-password
                  placeholder="请输入密码"
                  :prefix-icon="Lock"
                  size="large"
                  @keyup.enter="onLogin"
                />
              </el-form-item>
              <el-button
                type="primary"
                class="submit-btn"
                size="large"
                :loading="loading"
                @click="onLogin"
              >
                {{ loginType === 'ADMIN' ? '进入管理后台' : '登 录' }}
              </el-button>
            </el-form>

            <div class="tip">
              <template v-if="loginType === 'ADMIN'">
                初始管理员账号：<b>admin</b> / <b>admin123</b>
              </template>
              <template v-else>
                初始用户账号：<b>demo</b> / <b>demo123</b>
              </template>
            </div>
          </el-tab-pane>

          <!-- 注册（仅用户端） -->
          <el-tab-pane v-if="loginType === 'USER'" label="注册" name="register">
            <el-form
              ref="regRef"
              :model="regForm"
              :rules="regRules"
              label-position="top"
              @submit.prevent
            >
              <el-form-item label="用户名" prop="username">
                <el-input
                  v-model="regForm.username"
                  placeholder="3-20位字母/数字/下划线"
                  :prefix-icon="User"
                  clearable
                />
              </el-form-item>
              <el-form-item label="昵称" prop="nickname">
                <el-input v-model="regForm.nickname" placeholder="选填" clearable />
              </el-form-item>
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="regForm.email" placeholder="选填" clearable />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="regForm.password"
                  type="password"
                  show-password
                  placeholder="6-32位"
                  :prefix-icon="Lock"
                />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirm">
                <el-input
                  v-model="regForm.confirm"
                  type="password"
                  show-password
                  placeholder="再次输入密码"
                  @keyup.enter="onRegister"
                />
              </el-form-item>
              <el-button
                type="primary"
                class="submit-btn"
                size="large"
                :loading="loading"
                @click="onRegister"
              >
                注 册
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  ChatLineRound,
  DataAnalysis,
  Lock,
  MagicStick,
  Setting,
  UploadFilled,
  User
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { register } from '@/api/auth'
import { homePathOf } from '@/router'

type LoginType = 'USER' | 'ADMIN'

const tab = ref('login')
const loading = ref(false)
const loginType = ref<LoginType>('USER')
const router = useRouter()
const route = useRoute()
const store = useUserStore()

const loginRef = ref<FormInstance>()
const regRef = ref<FormInstance>()
const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', confirm: '', nickname: '', email: '' })

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const regRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度须为3-20位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '仅支持字母数字下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须为6-32位', trigger: 'blur' }
  ],
  confirm: [
    {
      validator: (_r, v: string, cb) => {
        if (!v) cb(new Error('请再次输入密码'))
        else if (v !== regForm.password) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

/** 切换登录入口，管理端不提供注册 */
function switchType(type: LoginType) {
  if (loginType.value === type) return
  loginType.value = type
  if (type === 'ADMIN' && tab.value === 'register') tab.value = 'login'
  loginForm.password = ''
  loginRef.value?.clearValidate()
}

// 从路由 query 预置入口（如 /login?type=admin）
watch(
  () => route.query.type,
  (v) => {
    if (v === 'admin') loginType.value = 'ADMIN'
  },
  { immediate: true }
)

async function onLogin() {
  const ok = await loginRef.value?.validate().catch(() => false)
  if (!ok) return
  loading.value = true
  try {
    const role = await store.login({
      username: loginForm.username,
      password: loginForm.password,
      loginType: loginType.value
    })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || homePathOf(role)
    router.push(redirect)
  } finally {
    loading.value = false
  }
}

async function onRegister() {
  const ok = await regRef.value?.validate().catch(() => false)
  if (!ok) return
  loading.value = true
  try {
    await register({
      username: regForm.username,
      password: regForm.password,
      nickname: regForm.nickname,
      email: regForm.email
    })
    ElMessage.success('注册成功，请登录')
    tab.value = 'login'
    loginForm.username = regForm.username
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
/* 主题变量：用户端蓝色 / 管理端紫色，切换入口时整体色调变化 */
.login-page {
  --brand-1: #2563eb;
  --brand-2: #38bdf8;
  --page-bg: linear-gradient(135deg, #0b1f3a 0%, #123a6b 55%, #1e4e8c 100%);

  position: relative;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--page-bg);
  transition: background 0.6s ease;

  &.theme-admin {
    --brand-1: #6d28d9;
    --brand-2: #a855f7;
    --page-bg: linear-gradient(135deg, #1b1035 0%, #2e1a5e 55%, #4c1d95 100%);
  }
}

/* 背景光斑 */
.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.45;
  pointer-events: none;
  transition: background 0.6s ease;
}
.orb-1 {
  width: 420px;
  height: 420px;
  top: -120px;
  left: -100px;
  background: var(--brand-1);
}
.orb-2 {
  width: 360px;
  height: 360px;
  bottom: -140px;
  right: -80px;
  background: var(--brand-2);
}
.orb-3 {
  width: 260px;
  height: 260px;
  top: 45%;
  left: 42%;
  background: var(--brand-1);
  opacity: 0.25;
}

/* 主容器 */
.login-shell {
  position: relative;
  z-index: 2;
  display: flex;
  width: 920px;
  max-width: calc(100vw - 48px);
  min-height: 560px;
  border-radius: 20px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 24px 70px rgba(4, 16, 38, 0.45);
  animation: shell-in 0.5s ease;
}
@keyframes shell-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 左侧品牌区 */
.brand-panel {
  position: relative;
  width: 42%;
  padding: 44px 34px;
  display: flex;
  flex-direction: column;
  color: #fff;
  background: linear-gradient(160deg, var(--brand-1), var(--brand-2));
  transition: background 0.6s ease;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(circle at 80% 15%, rgba(255, 255, 255, 0.22), transparent 55%);
    pointer-events: none;
  }
}
.brand-logo {
  width: 58px;
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.3);
}
.brand-title {
  margin: 22px 0 10px;
  font-size: 22px;
  line-height: 1.4;
  font-weight: 700;
}
.brand-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.85);
}
.brand-features {
  list-style: none;
  margin: 32px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 15px;

  li {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.92);

    .el-icon {
      width: 26px;
      height: 26px;
      border-radius: 8px;
      background: rgba(255, 255, 255, 0.16);
      font-size: 14px;
    }
  }
}
.brand-footer {
  margin-top: auto;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
}

/* 右侧表单区 */
.form-panel {
  flex: 1;
  padding: 36px 42px 28px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

/* 入口切换 */
.entry-switch {
  position: relative;
  display: flex;
  padding: 4px;
  border-radius: 12px;
  background: #f1f5f9;
  margin-bottom: 24px;
}
.entry-item {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  border: none;
  background: transparent;
  border-radius: 9px;
  font-size: 14px;
  color: #64748b;
  cursor: pointer;
  transition: color 0.25s ease;

  &.active {
    color: var(--brand-1);
    font-weight: 600;
  }
}
.entry-slider {
  position: absolute;
  z-index: 1;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: 40px;
  border-radius: 9px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.12);
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);

  &.right {
    transform: translateX(100%);
  }
}

.form-head {
  margin-bottom: 6px;

  h2 {
    margin: 0 0 6px;
    font-size: 20px;
    color: #0f172a;
  }
  p {
    margin: 0;
    font-size: 13px;
    color: #94a3b8;
  }
}

/* 表单 */
.auth-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 14px;
  }
  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
    background: #eef2f7;
  }
  :deep(.el-tabs__item) {
    font-size: 14px;
  }
  :deep(.el-tabs__active-bar) {
    background-color: var(--brand-1);
  }
  :deep(.el-tabs__item.is-active) {
    color: var(--brand-1);
  }
  :deep(.el-form-item__label) {
    padding-bottom: 4px;
    font-size: 13px;
    color: #475569;
  }
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
  background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
  border: none;
  font-weight: 600;
  letter-spacing: 2px;
  transition: opacity 0.25s ease, transform 0.15s ease;

  &:hover {
    opacity: 0.92;
  }
  &:active {
    transform: scale(0.99);
  }
}

.tip {
  margin-top: 16px;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;

  b {
    color: #475569;
  }
}

/* 窄屏隐藏品牌区，表单占满 */
@media (max-width: 768px) {
  .brand-panel {
    display: none;
  }
  .login-shell {
    width: 420px;
    min-height: auto;
  }
  .form-panel {
    padding: 32px 26px;
  }
}
</style>
