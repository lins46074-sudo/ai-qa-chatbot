<template>
  <div class="admin-docs">
    <!-- 统计提示 + 工具条 -->
    <div class="aidoc-card toolbar">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="全库文档资源统计：可检索任意用户上传的文档并执行管理端强删"
        style="flex: 1"
      />
      <el-input v-model="query.keyword" placeholder="按文件名搜索" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
    </div>

    <!-- 文档表格 -->
    <div class="aidoc-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="文件名" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-icon :color="row.fileType === 'PDF' ? '#d4380d' : '#d48806'"><Document /></el-icon>
            <span style="margin-left: 6px">{{ row.fileName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userName" label="上传人" width="120" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.fileType === 'PDF' ? 'danger' : 'warning'" effect="plain" size="small">{{ row.fileType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="字符数" width="100">
          <template #default="{ row }">{{ row.charCount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">就绪</el-tag>
            <el-tag v-else-if="row.status === 2" type="danger" size="small">失败</el-tag>
            <el-tag v-else type="warning" size="small">解析中</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { deleteAdminDoc, pageAdminDocs } from '@/api/admin'
import type { DocumentVO } from '@/api/types'

const loading = ref(false)
const list = ref<DocumentVO[]>([])
const total = ref(0)
const query = reactive<{ current: number; size: number; keyword?: string }>({ current: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const data = await pageAdminDocs(query)
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

async function onDelete(row: DocumentVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.fileName}」（上传人 ${row.userName || '-'}）吗？其下会话将被级联清理。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteAdminDoc(row.id)
  ElMessage.success('文档已删除')
  load()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

onMounted(load)
</script>

<style scoped lang="scss">
.admin-docs {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
</style>
