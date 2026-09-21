<template>
  <div class="workspace">
    <!-- 左侧面板：会话 / 我的文档 -->
    <aside class="side-panel">
      <div class="side-head">
        <el-button type="primary" :icon="Plus" class="new-btn" @click="newSession">
          新建对话
        </el-button>
      </div>

      <!-- 分段式切换 -->
      <div class="side-tabs">
        <button
          :class="['tab', { active: leftTab === 'session' }]"
          @click="leftTab = 'session'"
        >
          <el-icon :size="14"><ChatSquare /></el-icon>
          会话
          <span v-if="sessions.length" class="tab-count">{{ sessions.length }}</span>
        </button>
        <button
          :class="['tab', { active: leftTab === 'doc' }]"
          @click="leftTab = 'doc'"
        >
          <el-icon :size="14"><FolderOpened /></el-icon>
          我的文档
          <span v-if="docs.length" class="tab-count">{{ docs.length }}</span>
        </button>
      </div>

      <!-- 会话列表 -->
      <el-scrollbar v-show="leftTab === 'session'" class="side-scroll">
        <div v-if="!sessions.length" class="empty-hint">
          <el-icon :size="30"><ChatDotRound /></el-icon>
          <p>暂无会话</p>
          <span>选择文档后提问将自动创建</span>
        </div>
        <div
          v-for="s in sessions"
          :key="s.id"
          :class="['session-item', { active: currentSession?.id === s.id }]"
          @click="openSession(s)"
        >
          <div class="s-title">
            <el-icon :size="14"><ChatSquare /></el-icon>
            <span class="ellipsis">{{ s.title }}</span>
            <el-icon class="del" :size="13" title="删除会话" @click.stop="removeSession(s)">
              <Delete />
            </el-icon>
          </div>
          <div class="s-meta">
            <span class="ellipsis doc-ref">{{ s.documentName }}</span>
            <span class="s-time">{{ formatTime(s.createTime) }} · {{ s.messageCount }} 条</span>
          </div>
        </div>
      </el-scrollbar>

      <!-- 文档列表 -->
      <el-scrollbar v-show="leftTab === 'doc'" class="side-scroll">
        <div v-if="!docs.length" class="empty-hint">
          <el-icon :size="30"><FolderOpened /></el-icon>
          <p>暂无文档</p>
          <span>请先到「我的文档」上传 PDF/TXT</span>
        </div>
        <div
          v-for="d in docs"
          :key="d.id"
          :class="['doc-item', { active: currentDoc?.id === d.id, disabled: d.status !== 1 }]"
          @click="selectDoc(d)"
        >
          <el-icon :size="16" :color="d.fileType === 'PDF' ? '#d4380d' : '#d48806'">
            <Document />
          </el-icon>
          <span class="ellipsis doc-name">{{ d.fileName }}</span>
          <el-tag v-if="d.status === 1" type="success" size="small" effect="plain">就绪</el-tag>
          <el-tag v-else-if="d.status === 2" type="danger" size="small" effect="plain">失败</el-tag>
          <el-tag v-else type="warning" size="small" effect="plain">解析中</el-tag>
        </div>
      </el-scrollbar>
    </aside>

    <!-- 右侧对话区 -->
    <section class="chat-area">
      <header class="chat-head">
        <div class="chat-titles">
          <span class="cur-doc" :class="{ placeholder: !currentDoc }">
            <el-icon :size="15"><FolderChecked /></el-icon>
            {{ currentDoc ? currentDoc.fileName : '请选择文档开始问答' }}
          </span>
          <span v-if="currentSession" class="cur-session">
            <el-icon :size="12"><ChatLineRound /></el-icon>
            {{ currentSession.title }}
          </span>
        </div>
        <div v-if="currentDoc" class="head-actions">
          <el-tooltip content="清空当前对话" placement="bottom">
            <el-button link :icon="Delete" :disabled="!messages.length" @click="clearChat" />
          </el-tooltip>
        </div>
      </header>

      <div ref="msgBoxRef" class="msg-box">
        <!-- 更早消息加载 -->
        <div v-if="hasMore" class="load-more">
          <el-button link type="primary" :loading="loadingMore" @click="loadOlder">加载更早消息</el-button>
        </div>

        <div v-if="!messages.length && !streaming" class="chat-empty">
          <el-empty description="基于私有文档的智能问答">
            <template #image>
              <div class="empty-illustration">
                <el-icon :size="56"><ChatLineRound /></el-icon>
              </div>
            </template>
            <p class="empty-tip">
              {{ currentDoc ? '输入问题，开始与文档对话' : '从左侧选择一个就绪文档开始' }}
            </p>
          </el-empty>
        </div>

        <!-- 消息列表 -->
        <template v-for="(m, i) in messages" :key="m.key">
          <!-- 用户消息 -->
          <div v-if="m.role === 'USER'" class="row user-row">
            <div class="bubble user-bubble">{{ m.content }}</div>
            <el-avatar :size="34" class="avatar user-avatar">我</el-avatar>
          </div>
          <!-- AI 消息 -->
          <div v-else class="row ai-row">
            <el-avatar :size="34" class="avatar ai-avatar">
              <el-icon><MagicStick /></el-icon>
            </el-avatar>
            <div class="bubble ai-bubble" :class="{ 'is-error': m.error }">
              <div class="ai-tag">AI</div>
              <div class="ai-content">
                <span class="ai-text">{{ m.content }}</span>
                <span v-if="m.streaming" class="typing-cursor" />
              </div>
              <!-- 引用溯源：展示本条回答所依据的文档片段，供用户逐句核对 -->
              <div v-if="m.sources && m.sources.length" class="ai-sources">
                <div class="sources-head" @click="toggleSources(m.key)">
                  <el-icon><Files /></el-icon>
                  <span class="sources-title">{{ m.sources.length }} 条引用来源</span>
                  <el-icon class="sources-arrow" :class="{ open: isSourcesOpen(m.key) }">
                    <ArrowDown />
                  </el-icon>
                </div>
                <el-collapse-transition>
                  <div v-show="isSourcesOpen(m.key)" class="sources-body">
                    <div v-for="s in m.sources" :key="s.refIndex" class="source-item">
                      <div class="source-meta">
                        <span class="source-ref">[{{ s.refIndex }}]</span>
                        <span class="source-index">第 {{ s.chunkIndex + 1 }} 段</span>
                        <span class="source-score">相似度 {{ (s.score * 100).toFixed(1) }}%</span>
                      </div>
                      <div class="source-text">{{ s.snippet }}</div>
                    </div>
                  </div>
                </el-collapse-transition>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区 -->
      <footer class="chat-input">
        <div class="input-wrap" :class="{ focused: inputFocused }">
          <el-input
            v-model="question"
            type="textarea"
            :rows="2"
            resize="none"
            :disabled="streaming"
            placeholder="输入问题，Enter 发送 / Shift+Enter 换行"
            @focus="inputFocused = true"
            @blur="inputFocused = false"
            @keydown.enter.exact.prevent="send"
          />
          <div class="input-foot">
            <span class="input-hint">
              {{ currentDoc ? `当前文档：${currentDoc.fileName}` : '未选择文档' }}
            </span>
            <el-button
              type="primary"
              :icon="streaming ? undefined : Promotion"
              :loading="streaming"
              :disabled="!canSend"
              class="send-btn"
              @click="send"
            >
              {{ streaming ? '生成中' : '发送' }}
            </el-button>
          </div>
        </div>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown, ChatDotRound, ChatLineRound, ChatSquare, Delete, Document,
  Files, FolderChecked, FolderOpened, MagicStick, Plus, Promotion
} from '@element-plus/icons-vue'
import { deleteSession, pageMessages, pageSessions } from '@/api/chat'
import { pageDocs } from '@/api/doc'
import { postStream } from '@/utils/sse'
import type { DocumentVO, SessionVO, SourceVO } from '@/api/types'

/** 每页历史消息条数 */
const PAGE_SIZE = 20

interface MsgItem {
  key: number
  id?: number
  role: 'USER' | 'ASSISTANT'
  content: string
  /** 引用来源：答案依据的文档片段（仅 AI 消息可能有值） */
  sources?: SourceVO[]
  streaming?: boolean
  error?: boolean
}

const route = useRoute()
const leftTab = ref<'session' | 'doc'>('session')
const sessions = ref<SessionVO[]>([])
const docs = ref<DocumentVO[]>([])
const currentDoc = ref<DocumentVO | null>(null)
const currentSession = ref<SessionVO | null>(null)
const messages = ref<MsgItem[]>([])
const question = ref('')
const streaming = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const pageCurrent = ref(1)
const inputFocused = ref(false)
const msgBoxRef = ref<HTMLDivElement>()
let msgKey = 0

/** 已展开引用来源的气泡 key 集合（默认折叠，避免长片段刷屏） */
const openSourceKeys = ref<Set<number>>(new Set())

/** 切换某条回答的引用来源展开状态 */
function toggleSources(key: number) {
  const keys = openSourceKeys.value
  if (keys.has(key)) keys.delete(key)
  else keys.add(key)
}

/** 判断某条回答的引用来源是否展开 */
function isSourcesOpen(key: number): boolean {
  return openSourceKeys.value.has(key)
}

const canSend = computed(
  () => !!currentDoc.value && !streaming.value && question.value.trim().length > 0
)

/* ---------------- 数据加载 ---------------- */

/** 加载会话与文档列表（并行） */
async function refreshLists() {
  const [sData, dData] = await Promise.all([
    pageSessions({ current: 1, size: 50 }),
    pageDocs({ current: 1, size: 50 })
  ])
  sessions.value = sData.records
  docs.value = dData.records
  // 支持从 /docs 携带 ?docId= 直达
  const docId = Number(route.query.docId)
  if (docId && !currentDoc.value) {
    const found = docs.value.find((d) => d.id === docId)
    if (found && found.status === 1) {
      selectDoc(found)
    }
  }
  // 默认选中第一个就绪文档（未指定时）
  if (!currentDoc.value) {
    const first = docs.value.find((d) => d.status === 1)
    if (first) selectDoc(first)
  }
}

/** 选中文档：若当前会话绑定其它文档则切回空会话 */
function selectDoc(doc: DocumentVO) {
  if (doc.status !== 1) {
    ElMessage.warning('该文档尚未就绪，无法问答')
    return
  }
  currentDoc.value = doc
  if (currentSession.value && currentSession.value.documentId !== doc.id) {
    resetChat()
  }
  leftTab.value = 'session'
}

/** 打开会话：加载该会话历史（最新一页） */
async function openSession(s: SessionVO) {
  currentSession.value = s
  const doc = docs.value.find((d) => d.id === s.documentId)
  if (doc) currentDoc.value = doc
  messages.value = []
  const data = await pageMessages(s.id, { current: 1, size: PAGE_SIZE })
  // 后端返回 id 倒序（最新在前），反转后正序展示
  messages.value = [...data.records].reverse().map(toMsg)
  pageCurrent.value = 1
  hasMore.value = data.current < data.pages
  scrollBottom(true)
}

/** 加载更早消息：向后翻页并头部拼接 */
async function loadOlder() {
  if (!currentSession.value) return
  loadingMore.value = true
  try {
    const next = pageCurrent.value + 1
    const data = await pageMessages(currentSession.value.id, { current: next, size: PAGE_SIZE })
    if (!data.records.length) {
      hasMore.value = false
      return
    }
    const older = [...data.records].reverse().map(toMsg)
    messages.value = [...older, ...messages.value]
    pageCurrent.value = next
    hasMore.value = data.current < data.pages
    // 保持滚动位置：记录旧高度
    const box = msgBoxRef.value
    if (box) {
      const before = box.scrollHeight
      nextTick(() => {
        box.scrollTop = box.scrollHeight - before + box.scrollTop
      })
    }
  } finally {
    loadingMore.value = false
  }
}

function toMsg(m: {
  id?: number
  role: string
  content: string
  sources?: SourceVO[] | null
  createTime?: string
}): MsgItem {
  return {
    key: ++msgKey,
    id: m.id,
    role: m.role === 'USER' ? 'USER' : 'ASSISTANT',
    content: m.content,
    // 历史消息的引用来源已随消息落库，打开会话时一并回显
    sources: m.sources ?? undefined
  }
}

/** 清空当前聊天上下文 */
function resetChat() {
  currentSession.value = null
  messages.value = []
  hasMore.value = false
  pageCurrent.value = 1
}

/** 新建会话（须先选择就绪文档） */
async function newSession() {
  if (!currentDoc.value) {
    ElMessage.warning('请先在「我的文档」选择一个就绪文档')
    leftTab.value = 'doc'
    return
  }
  resetChat()
}

/** 清空当前对话（仅清空视图，不删除历史会话） */
async function clearChat() {
  try {
    await ElMessageBox.confirm('确定清空当前对话视图吗？历史会话仍保留在左侧列表。', '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  resetChat()
}

/** 删除会话 */
async function removeSession(s: SessionVO) {
  try {
    await ElMessageBox.confirm(`确定删除会话「${s.title}」吗？历史消息将一并清除。`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  await deleteSession(s.id)
  ElMessage.success('会话已删除')
  if (currentSession.value?.id === s.id) resetChat()
  const data = await pageSessions({ current: 1, size: 50 })
  sessions.value = data.records
}

/* ---------------- 流式问答 ---------------- */

async function send() {
  const q = question.value.trim()
  if (!q) return
  if (!currentDoc.value) {
    ElMessage.warning('请先选择一个就绪文档')
    return
  }
  if (streaming.value) return

  // 1. 立即回显用户消息并创建占位 AI 气泡
  messages.value.push({ key: ++msgKey, role: 'USER', content: q })
  const aiMsg: MsgItem = { key: ++msgKey, role: 'ASSISTANT', content: '', streaming: true }
  messages.value.push(aiMsg)
  scrollBottom()

  question.value = ''
  streaming.value = true
  let createdSessionId: number | null = null

  // 2. 打开 SSE 流（fetch 直连，绕开 axios）
  await postStream(
    '/chat/stream',
    {
      documentId: currentDoc.value.id,
      sessionId: currentSession.value?.id ?? null,
      question: q
    },
    {
      onSession(sessionId) {
        // 服务端自动新建会话：绑定 id 并插入会话列表
        createdSessionId = sessionId
        currentSession.value = {
          id: sessionId,
          userId: 0,
          documentId: currentDoc.value!.id,
          documentName: currentDoc.value!.fileName,
          title: short(q),
          messageCount: 0,
          createTime: '',
          updateTime: ''
        }
        sessions.value.unshift(currentSession.value)
      },
      onSources(sources) {
        // 引用来源在正文之前下发：先挂上去，答案流式输出时依据面板已就绪
        aiMsg.sources = sources
      },
      onDelta(text) {
        aiMsg.content += text
        scrollBottom()
      },
      onDone() {
        aiMsg.streaming = false
        refreshAfterDone(createdSessionId)
      },
      onError(msg) {
        aiMsg.streaming = false
        aiMsg.error = true
        if (aiMsg.content === '') aiMsg.content = msg
        else aiMsg.content += `\n\n[错误] ${msg}`
        scrollBottom()
      }
    }
  )
  streaming.value = false
}

/** 流结束后：静默刷新会话列表（消息数/标题） */
async function refreshAfterDone(createdSessionId: number | null) {
  const data = await pageSessions({ current: 1, size: 50 })
  sessions.value = data.records
  // 若本轮自动建了会话，从最新列表里回填完整信息并保持选中
  if (createdSessionId && currentSession.value) {
    const full = sessions.value.find((s) => s.id === createdSessionId)
    if (full) currentSession.value = full
  }
  scrollBottom(true)
}

function short(q: string): string {
  const s = q.replace(/\s+/g, ' ')
  return s.length > 15 ? s.slice(0, 15) : s
}

function scrollBottom(force = false) {
  nextTick(() => {
    const box = msgBoxRef.value
    if (box && (force || streaming.value)) {
      box.scrollTop = box.scrollHeight
    }
  })
}

function formatTime(t: string): string {
  if (!t) return ''
  return t.length > 16 ? t.slice(5, 16) : t
}

// 会话切换后清空当前消息
watch(currentSession, () => {
  if (!currentSession.value) resetChat()
})

onMounted(refreshLists)
</script>

<style scoped lang="scss">
.workspace {
  height: calc(100vh - 88px);
  display: flex;
  gap: 16px;
}

/* ---------- 左侧面板 ---------- */
.side-panel {
  width: 296px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(18, 44, 82, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.side-head {
  padding: 14px 14px 10px;
  .new-btn {
    width: 100%;
    height: 38px;
    border-radius: 9px;
    font-weight: 600;
    box-shadow: 0 4px 12px rgba(30, 78, 140, 0.22);
  }
}
.side-tabs {
  display: flex;
  gap: 6px;
  margin: 0 14px 10px;
  padding: 4px;
  background: #f2f5fa;
  border-radius: 10px;
  .tab {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    padding: 7px 0;
    border: none;
    background: transparent;
    border-radius: 7px;
    cursor: pointer;
    font-size: 13px;
    color: var(--aidoc-text-sub);
    transition: all 0.18s;
    .tab-count {
      min-width: 16px;
      height: 16px;
      padding: 0 4px;
      border-radius: 8px;
      background: rgba(30, 78, 140, 0.1);
      color: var(--el-color-primary);
      font-size: 10px;
      line-height: 16px;
      text-align: center;
    }
    &:hover {
      color: var(--el-color-primary);
    }
    &.active {
      background: #fff;
      color: var(--el-color-primary);
      font-weight: 600;
      box-shadow: 0 1px 4px rgba(18, 44, 82, 0.1);
    }
  }
}
.side-scroll {
  flex: 1;
}
.empty-hint {
  padding: 40px 20px;
  color: var(--aidoc-text-sub);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  p {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--aidoc-text-main);
  }
  span {
    font-size: 12px;
    opacity: 0.75;
  }
}
.session-item,
.doc-item {
  margin: 0 8px 4px;
  padding: 10px 12px;
  border-radius: 9px;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
  &:hover {
    background: var(--el-color-primary-light-9);
  }
  &.active {
    background: var(--el-color-primary-light-9);
    box-shadow: inset 3px 0 0 var(--el-color-primary);
  }
}
.session-item {
  .s-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 500;
    color: var(--aidoc-text-main);
    .ellipsis {
      flex: 1;
    }
  }
  .s-meta {
    margin-top: 5px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 11px;
    color: var(--aidoc-text-sub);
    .doc-ref {
      max-width: 110px;
      opacity: 0.85;
    }
    .s-time {
      flex: 1;
      white-space: nowrap;
      text-align: right;
    }
    .del {
      visibility: hidden;
      color: #f56c6c;
      transition: transform 0.15s;
      &:hover {
        transform: scale(1.2);
      }
    }
  }
  &:hover .del {
    visibility: visible;
  }
}
.doc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  .doc-name {
    flex: 1;
    font-size: 13px;
  }
  &.disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

/* ---------- 对话区 ---------- */
.chat-area {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(18, 44, 82, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid #eef2f8;
  .chat-titles {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 0;
  }
  .cur-doc {
    display: flex;
    align-items: center;
    gap: 5px;
    font-weight: 600;
    color: var(--el-color-primary);
    font-size: 14px;
    max-width: 46%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    &.placeholder {
      color: var(--aidoc-text-sub);
      font-weight: 400;
    }
  }
  .cur-session {
    display: flex;
    align-items: center;
    gap: 4px;
    color: var(--aidoc-text-sub);
    font-size: 12px;
    border-left: 1px solid #e0e7f1;
    padding-left: 14px;
    max-width: 46%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .head-actions {
    flex-shrink: 0;
  }
}
.msg-box {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  background: var(--aidoc-bg);
}
.chat-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  .empty-illustration {
    width: 108px;
    height: 108px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    color: var(--el-color-primary);
    background: radial-gradient(circle at 50% 40%, #e8f0fb 0%, #f4f8fd 70%);
    box-shadow: 0 8px 24px rgba(30, 78, 140, 0.12);
  }
  .empty-tip {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--aidoc-text-sub);
  }
}
.load-more {
  text-align: center;
  margin-bottom: 10px;
}
.row {
  display: flex;
  margin-bottom: 18px;
  animation: fade-up 0.28s ease;
  &.user-row {
    justify-content: flex-end;
  }
  &.ai-row {
    justify-content: flex-start;
  }
}
@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.avatar {
  flex-shrink: 0;
  font-size: 13px;
}
.user-avatar {
  margin-left: 10px;
  background: var(--el-color-primary);
  box-shadow: 0 2px 8px rgba(30, 78, 140, 0.28);
}
.ai-avatar {
  margin-right: 10px;
  background: #fff;
  border: 1px solid var(--el-color-primary);
  color: var(--el-color-primary);
  box-shadow: 0 2px 8px rgba(30, 78, 140, 0.12);
}
.bubble {
  max-width: 70%;
  padding: 11px 15px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
}
.user-bubble {
  background: linear-gradient(135deg, #2a6bb5 0%, #1e4e8c 100%);
  color: #fff;
  border-top-right-radius: 3px;
  white-space: pre-wrap;
  box-shadow: 0 4px 14px rgba(30, 78, 140, 0.22);
}
.ai-bubble {
  background: #fff;
  border: 1px solid #e3eaf3;
  border-top-left-radius: 3px;
  box-shadow: 0 2px 10px rgba(18, 44, 82, 0.05);
  &.is-error {
    border-color: #fbc4c4;
    background: #fff8f8;
  }
}
.ai-tag {
  display: inline-block;
  font-size: 10px;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 4px;
  padding: 1px 6px;
  margin-bottom: 7px;
  line-height: 1.6;
}
.ai-content {
  .ai-text {
    white-space: pre-wrap;
  }
}
/* ---------- 引用溯源面板 ---------- */
.ai-sources {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed #e3eaf3;
}
.sources-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
  user-select: none;
  padding: 2px 0;
  transition: color 0.18s;
  &:hover {
    color: #2a6bb5;
  }
  .sources-title {
    flex: 1;
  }
  .sources-arrow {
    transition: transform 0.2s;
    &.open {
      transform: rotate(180deg);
    }
  }
}
.sources-body {
  margin-top: 8px;
}
.source-item {
  background: #f7fafd;
  border: 1px solid #e8eff8;
  border-left: 3px solid var(--el-color-primary-light-5);
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  &:last-child {
    margin-bottom: 0;
  }
}
.source-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #8a97a8;
  margin-bottom: 5px;
  .source-ref {
    color: var(--el-color-primary);
    font-weight: 600;
  }
  .source-score {
    margin-left: auto;
  }
}
.source-text {
  font-size: 12.5px;
  line-height: 1.7;
  color: #4a5566;
  white-space: pre-wrap;
  max-height: 120px;
  overflow-y: auto;
}
.chat-input {
  padding: 14px 18px 16px;
  border-top: 1px solid #eef2f8;
  background: #fff;
  .input-wrap {
    border: 1px solid #dfe6f0;
    border-radius: 12px;
    padding: 8px 10px 8px 14px;
    transition: border-color 0.18s, box-shadow 0.18s;
    &.focused {
      border-color: var(--el-color-primary);
      box-shadow: 0 0 0 3px rgba(30, 78, 140, 0.1);
    }
    :deep(.el-textarea__inner) {
      border: none;
      box-shadow: none;
      padding: 4px 0;
      font-size: 14px;
      background: transparent;
    }
  }
  .input-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 6px;
    .input-hint {
      font-size: 12px;
      color: var(--aidoc-text-sub);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .send-btn {
      flex-shrink: 0;
      border-radius: 9px;
      padding: 8px 20px;
      font-weight: 600;
    }
  }
}
</style>
