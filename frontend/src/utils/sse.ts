import { storage } from './storage'
import type { SourceVO } from '@/api/types'

/** SSE 流式回调句柄 */
export interface StreamHandlers {
  /** 服务端自动新建会话时回调（携带 sessionId） */
  onSession?: (sessionId: number) => void
  /** 检索到的引用来源（在 delta 之前下发，无命中时为空数组） */
  onSources?: (sources: SourceVO[]) => void
  /** 每收到一段增量内容 */
  onDelta: (text: string) => void
  /** 流结束 */
  onDone: () => void
  /** 错误（服务端 error 事件或网络错误） */
  onError: (msg: string) => void
}

/**
 * 基于 fetch 的 SSE 流式请求（axios 不适合流式读取）：
 * 按 '\n\n' 切分事件块，解析 'data:' 前缀的 JSON 事件负载
 */
export async function postStream(url: string, body: object, h: StreamHandlers): Promise<void> {
  let resp: Response
  try {
    resp = await fetch(`${import.meta.env.VITE_API_BASE || '/api'}${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${storage.getToken()}`
      },
      body: JSON.stringify(body)
    })
  } catch (e) {
    h.onError('网络异常，无法连接问答服务')
    return
  }
  if (!resp.ok || !resp.body) {
    h.onError(`请求失败（HTTP ${resp.status}）`)
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // 按空行切分 SSE 事件块
      let idx: number
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, idx).trim()
        buffer = buffer.slice(idx + 2)
        if (!block) continue
        for (const line of block.split('\n')) {
          if (!line.startsWith('data:')) continue
          let ev: any
          try {
            ev = JSON.parse(line.slice(5).trim())
          } catch {
            continue // 忽略无法解析的行（如 keep-alive）
          }
          if (ev.event === 'session') h.onSession?.(ev.sessionId)
          else if (ev.event === 'sources') h.onSources?.(ev.sources || [])
          else if (ev.event === 'delta') h.onDelta(ev.content || '')
          else if (ev.event === 'done') h.onDone()
          else if (ev.event === 'error') h.onError(ev.message || '服务异常')
        }
      }
    }
  } catch {
    // 读取中断（如组件卸载）静默处理
  }
}
