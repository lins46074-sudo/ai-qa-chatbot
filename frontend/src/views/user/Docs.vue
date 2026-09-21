<template>
  <div class="docs-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="head-left">
        <h2>我的文档</h2>
        <p>上传 PDF / TXT 文档，构建你的私有知识库</p>
      </div>
      <el-upload
        :auto-upload="false"
        :show-file-list="false"
        accept=".pdf,.txt"
        :disabled="uploading"
        :on-change="onFileChange"
      >
        <el-button type="primary" :icon="Upload" :loading="uploading" class="upload-btn">
          上传文档
        </el-button>
      </el-upload>
    </div>

    <!-- 概览统计 -->
    <div class="stat-row">
      <div class="stat-card aidoc-card">
        <div class="stat-icon"><el-icon :size="20"><Files /></el-icon></div>
        <div class="stat-body">
          <span class="stat-num">{{ total }}</span>
          <span class="stat-label">文档总数</span>
        </div>
      </div>
      <div class="stat-card aidoc-card">
        <div class="stat-icon ready"><el-icon :size="20"><CircleCheck /></el-icon></div>
        <div class="stat-body">
          <span class="stat-num">{{ readyCount }}</span>
          <span class="stat-label">已就绪</span>
        </div>
      </div>
      <div class="stat-card aidoc-card">
        <div class="stat-icon pending"><el-icon :size="20"><Loading /></el-icon></div>
        <div class="stat-body">
          <span class="stat-num">{{ pendingCount }}</span>
          <span class="stat-label">解析中</span>
        </div>
      </div>
      <div class="stat-card aidoc-card">
        <div class="stat-icon total"><el-icon :size="20"><DataLine /></el-icon></div>
        <div class="stat-body">
          <span class="stat-num">{{ totalSizeText }}</span>
          <span class="stat-label">本页容量</span>
        </div>
      </div>
    </div>

    <!-- 上传进度 -->
    <transition name="fade">
      <div v-if="uploading" class="upload-progress aidoc-card">
        <div class="up-head">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span class="up-text">{{ uploadText }}</span>
          <span class="up-percent">{{ uploadPercent }}%</span>
        </div>
        <el-progress
          :percentage="uploadPercent"
          :status="uploadPercent >= 100 ? 'success' : ''"
          :stroke-width="10"
          :show-text="false"
        />
      </div>
    </transition>

    <!-- 文档表格 -->
    <div class="aidoc-card table-card">
      <div class="table-toolbar">
        <span class="tb-title">文档列表</span>
        <div class="filters">
          <el-input
            v-model="query.keyword"
            placeholder="按文件名搜索"
            clearable
            :prefix-icon="Search"
            style="width: 210px"
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.fileType" placeholder="类型" clearable style="width: 110px" @change="search">
            <el-option label="PDF" value="PDF" />
            <el-option label="TXT" value="TXT" />
          </el-select>
          <el-select v-model="query.status" placeholder="解析状态" clearable style="width: 130px" @change="search">
            <el-option label="解析中" :value="0" />
            <el-option label="就绪" :value="1" />
            <el-option label="失败" :value="2" />
          </el-select>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column label="文件名" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="file-cell">
              <div class="file-badge" :class="row.fileType === 'PDF' ? 'pdf' : 'txt'">
                {{ row.fileType }}
              </div>
              <span class="file-name">{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="内容量" width="110">
          <template #default="{ row }">{{ row.charCount }} 字</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 2" :content="row.failReason || '解析失败'" placement="top">
              <el-tag type="danger" size="small" effect="light">解析失败</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.status === 0" type="warning" size="small" effect="light">
              <span class="pulse">●</span> 解析中
            </el-tag>
            <el-tag v-else type="success" size="small" effect="light">就绪</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status !== 1" @click="goChat(row)">
              <el-icon><ChatLineRound /></el-icon> 去问答
            </el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无文档，点击右上角上传 PDF/TXT" />
        </template>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="changePage"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatLineRound, CircleCheck, DataLine, Files, Loading, Search, Upload
} from '@element-plus/icons-vue'
import SparkMD5 from 'spark-md5'
import { chunkStatus, deleteDoc, mergeChunks, pageDocs, uploadChunk, uploadDoc } from '@/api/doc'
import type { DocumentVO } from '@/api/types'

/** 分片大小（与后端 aidoc.upload.chunk-size 保持一致，2MB） */
const CHUNK_SIZE = 2 * 1024 * 1024
/** 直传阈值：≤2MB 走直传接口，避免无谓分片 */
const DIRECT_LIMIT = 2 * 1024 * 1024

const router = useRouter()
const loading = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)
const uploadText = ref('')
const list = ref<DocumentVO[]>([])
const total = ref(0)

const query = reactive<{ current: number; size: number; keyword?: string; fileType?: string; status?: number }>({
  current: 1,
  size: 10,
  keyword: undefined,
  fileType: undefined,
  status: undefined
})

/** 当前页已就绪文档数 */
const readyCount = computed(() => list.value.filter((d) => d.status === 1).length)
/** 当前页解析中文档数 */
const pendingCount = computed(() => list.value.filter((d) => d.status === 0).length)
/** 当前页文档总容量 */
const totalSizeText = computed(() =>
  formatSize(list.value.reduce((sum, d) => sum + (d.fileSize || 0), 0))
)

/** 加载分页数据（silent=true 时不显示表格 loading，供轮询使用，避免列表闪烁） */
async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const data = await pageDocs(query)
    list.value = data.records
    total.value = data.total
  } finally {
    if (!silent) loading.value = false
  }
}

/* ---------------- 解析状态轮询 ---------------- */

/** 轮询定时器句柄 */
let pollTimer: number | null = null
/** 本轮已轮询次数 */
let pollCount = 0
/** 轮询间隔（毫秒） */
const POLL_INTERVAL = 3000
/** 最大轮询次数，避免异常情况下无限轮询 */
const POLL_MAX_TIMES = 40

/** 停止轮询 */
function stopPoll() {
  if (pollTimer !== null) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

/**
 * 启动解析状态轮询：当前页存在「解析中(status=0)」文档时定时静默刷新，
 * 全部解析结束或达到最大次数后自动停止。
 *
 * <p>用递归 setTimeout 而非 setInterval —— 必须等上一次请求返回再排下一次，
 * 否则慢请求会不断堆积。</p>
 */
function startPoll() {
  stopPoll()
  pollCount = 0
  tickPoll()
}

/** 单次轮询调度 */
function tickPoll() {
  // 终止条件：已达最大次数，或已无解析中的文档
  if (pollCount >= POLL_MAX_TIMES) return
  if (!list.value.some((d) => d.status === 0)) return
  pollCount++
  pollTimer = window.setTimeout(async () => {
    await load(true)
    tickPoll()
  }, POLL_INTERVAL)
}

function search() {
  query.current = 1
  load().then(startPoll)
}

/** 翻页（query.current 已由 el-pagination 通过 v-model 更新） */
function changePage() {
  load().then(startPoll)
}

/** el-upload 选择文件回调（auto-upload=false） */
async function onFileChange(file: { raw?: File }) {
  const raw = file.raw
  if (!raw) return
  const ok = validateFile(raw)
  if (!ok) return
  uploading.value = true
  uploadPercent.value = 0
  try {
    if (raw.size <= DIRECT_LIMIT) {
      uploadText.value = '小文件直传中…'
      const doc = await uploadDoc(raw)
      handleUploadResult(doc)
    } else {
      await uploadByChunks(raw)
    }
  } finally {
    uploading.value = false
  }
}

/** 文件类型与空文件校验 */
function validateFile(f: File): boolean {
  const name = f.name.toLowerCase()
  if (!name.endsWith('.pdf') && !name.endsWith('.txt')) {
    ElMessage.warning('仅支持 PDF / TXT 文档')
    return false
  }
  if (f.size <= 0) {
    ElMessage.warning('文件内容为空')
    return false
  }
  return true
}

/** 大文件分片上传：MD5 → 断点查询 → 顺序补传缺失分片 → 合并 */
async function uploadByChunks(file: File) {
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)

  // 1. 计算全文 MD5（增量读入，避免大文件一次性载入内存）
  uploadText.value = '计算文件 MD5（秒传校验）…'
  const identifier = await calcFileMd5(file)

  // 2. 查询已上传分片（断点续传 / 秒传）
  const st = await chunkStatus(identifier)
  const uploadedSet = new Set<number>(st.uploaded || [])
  if (uploadedSet.size === totalChunks) {
    uploadText.value = '已存在相同文件（秒传），正在合并…'
  }

  // 3. 顺序上传缺失分片（幂等接口，网络重试安全）
  let done = uploadedSet.size
  for (let index = 0; index < totalChunks; index++) {
    if (uploadedSet.has(index)) continue
    const start = index * CHUNK_SIZE
    const blob = file.slice(start, Math.min(start + CHUNK_SIZE, file.size))
    const fd = new FormData()
    fd.append('file', blob, `${file.name}.part${index}`)
    fd.append('identifier', identifier)
    fd.append('index', String(index))
    fd.append('totalChunks', String(totalChunks))
    await uploadChunk(fd)
    done++
    uploadPercent.value = Math.floor((done / totalChunks) * 100)
    uploadText.value = `上传分片 ${done}/${totalChunks}`
  }

  // 4. 合并分片并同步解析（后端校验 MD5 一致性）
  uploadPercent.value = 100
  uploadText.value = '合并分片并解析文档…'
  const doc = await mergeChunks({ identifier, fileName: file.name, totalChunks, fileSize: file.size })
  handleUploadResult(doc)
}

/** 计算文件 MD5 */
function calcFileMd5(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()
    let pos = 0
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.onload = (e) => {
      spark.append(e.target?.result as ArrayBuffer)
      pos += CHUNK_SIZE
      if (pos < file.size) {
        loadNext()
      } else {
        resolve(spark.end())
      }
    }
    const loadNext = () => reader.readAsArrayBuffer(file.slice(pos, pos + CHUNK_SIZE))
    loadNext()
  })
}

/** 处理上传/合并后的结果（同步解析完成，返回最终状态） */
function handleUploadResult(doc: DocumentVO) {
  if (doc.status === 1) {
    ElMessage.success('上传并解析成功')
  } else if (doc.status === 2) {
    ElMessage.warning(`文档解析失败：${doc.failReason || '未知原因'}`)
  } else {
    // 解析中：刷新列表后由轮询接管，解析完成会自动更新为「就绪」
    ElMessage.info('文档已上传，解析中…')
  }
  load().then(startPoll)
}

/** 删除文档 */
async function onDelete(row: DocumentVO) {
  try {
    await ElMessageBox.confirm(`确定删除文档「${row.fileName}」吗？其下会话记录将一并删除。`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  await deleteDoc(row.id)
  ElMessage.success('删除成功')
  load()
}

/** 携带 docId 跳转问答工作台 */
function goChat(row: DocumentVO) {
  router.push({ path: '/workspace', query: { docId: String(row.id) } })
}

/** 字节数人性化展示 */
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

onMounted(() => load().then(startPoll))

// 离开页面时停止轮询，避免组件卸载后定时器仍在请求接口
onUnmounted(stopPoll)
</script>

<style scoped lang="scss">
.docs-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ---------- 页头 ---------- */
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  .head-left {
    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 700;
      color: var(--aidoc-text-main);
    }
    p {
      margin: 4px 0 0;
      font-size: 13px;
      color: var(--aidoc-text-sub);
    }
  }
  .upload-btn {
    height: 40px;
    border-radius: 9px;
    font-weight: 600;
    box-shadow: 0 4px 12px rgba(30, 78, 140, 0.22);
  }
}

/* ---------- 统计卡 ---------- */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  transition: transform 0.18s, box-shadow 0.18s;
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 22px rgba(18, 44, 82, 0.1);
  }
  .stat-icon {
    width: 44px;
    height: 44px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    &.ready {
      color: #2f9e44;
      background: #e9f7ef;
    }
    &.pending {
      color: #d48806;
      background: #fdf6e3;
    }
    &.total {
      color: #7048e8;
      background: #f0ebff;
    }
  }
  .stat-body {
    display: flex;
    flex-direction: column;
    min-width: 0;
    .stat-num {
      font-size: 22px;
      font-weight: 700;
      line-height: 1.2;
      color: var(--aidoc-text-main);
    }
    .stat-label {
      font-size: 12px;
      color: var(--aidoc-text-sub);
    }
  }
}

/* ---------- 上传进度 ---------- */
.upload-progress {
  padding: 14px 18px;
  .up-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
    color: var(--el-color-primary);
    font-size: 13px;
    .up-text {
      flex: 1;
      color: var(--aidoc-text-main);
    }
    .up-percent {
      font-weight: 600;
      color: var(--el-color-primary);
    }
  }
}

/* ---------- 表格 ---------- */
.table-card {
  .table-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    flex-wrap: wrap;
    margin-bottom: 14px;
    .tb-title {
      font-size: 15px;
      font-weight: 600;
      color: var(--aidoc-text-main);
      position: relative;
      padding-left: 11px;
      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 4px;
        height: 15px;
        border-radius: 2px;
        background: var(--el-color-primary);
      }
    }
  }
  .filters {
    display: flex;
    gap: 10px;
  }
  .file-cell {
    display: flex;
    align-items: center;
    gap: 9px;
    .file-badge {
      flex-shrink: 0;
      width: 34px;
      height: 22px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 5px;
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.3px;
      &.pdf {
        color: #d4380d;
        background: #fff1e8;
      }
      &.txt {
        color: #d48806;
        background: #fdf6e3;
      }
    }
    .file-name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  .pulse {
    animation: pulse 1.2s infinite;
    margin-right: 2px;
  }
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
@keyframes pulse {
  50% {
    opacity: 0.2;
  }
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
