<template>
  <div class="users-page">
    <!-- 工具栏 -->
    <div class="aidoc-card toolbar">
      <el-input v-model="query.keyword" placeholder="用户名 / 昵称" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.role" placeholder="角色" clearable style="width: 120px" @change="search">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="普通用户" value="USER" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
        <el-option label="正常" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
    </div>

    <!-- 用户表格 -->
    <div class="aidoc-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'" effect="dark" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :disabled="row.id === myId"
              @change="(v: boolean | string | number) => toggleStatus(row, Boolean(v))"
            />
          </template>
        </el-table-column>
        <el-table-column prop="docCount" label="文档" width="80" />
        <el-table-column prop="sessionCount" label="会话" width="80" />
        <el-table-column prop="createTime" label="注册时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReset(row)">重置密码</el-button>
            <el-button link type="danger" :disabled="row.id === myId" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </div>

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="createVisible" title="新增用户" width="440px">
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="3-20位字母/数字/下划线" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="createForm.nickname" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="createForm.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="6-32位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetVisible" title="重置密码" width="400px">
      <el-form ref="resetRef" :model="resetForm" :rules="resetRules" label-width="80px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="6-32位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReset">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createUser, deleteUser, pageUsers, resetPassword, setUserStatus } from '@/api/admin'
import type { AdminUserVO } from '@/api/types'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const myId = store.user?.id

const loading = ref(false)
const list = ref<AdminUserVO[]>([])
const total = ref(0)
const query = reactive<{ current: number; size: number; keyword?: string; role?: string; status?: number }>({
  current: 1,
  size: 10
})

const createVisible = ref(false)
const resetVisible = ref(false)
const submitting = ref(false)
const currentRow = ref<AdminUserVO | null>(null)

const createRef = ref<FormInstance>()
const resetRef = ref<FormInstance>()
const createForm = reactive({ username: '', password: '', nickname: '', email: '' })
const resetForm = reactive({ newPassword: '' })

const createRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度须为3-20位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '仅支持字母数字下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须为6-32位', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}
const resetRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须为6-32位', trigger: 'blur' }
  ]
}

async function load() {
  loading.value = true
  try {
    const data = await pageUsers(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}

function openCreate() {
  Object.assign(createForm, { username: '', password: '', nickname: '', email: '' })
  createVisible.value = true
}

async function submitCreate() {
  const ok = await createRef.value?.validate().catch(() => false)
  if (!ok) return
  submitting.value = true
  try {
    await createUser({ ...createForm })
    ElMessage.success('用户创建成功')
    createVisible.value = false
    search()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: AdminUserVO, enable: boolean) {
  await setUserStatus(row.id, enable ? 1 : 0)
  ElMessage.success(enable ? '已启用' : '已禁用并强制下线')
  load()
}

function openReset(row: AdminUserVO) {
  currentRow.value = row
  resetForm.newPassword = ''
  resetVisible.value = true
}

async function submitReset() {
  if (!currentRow.value) return
  const ok = await resetRef.value?.validate().catch(() => false)
  if (!ok) return
  submitting.value = true
  try {
    await resetPassword(currentRow.value.id, resetForm.newPassword)
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } finally {
    submitting.value = false
  }
}

async function onDelete(row: AdminUserVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」吗？其全部文档、会话将被级联删除，此操作不可恢复。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteUser(row.id)
  ElMessage.success('用户已删除')
  load()
}

onMounted(load)
</script>

<style scoped lang="scss">
.users-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
</style>
