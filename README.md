# Java RAG 私有文档 AI 问答系统

> 基于 Java 开发的 AI 问答系统，支持知识库检索与多轮对话，接入大模型实现 **RAG 检索增强生成**，降低 LLM 幻觉。
>
> 前后端分离的私有文档知识库问答平台。用户上传 PDF / TXT 后，系统在服务端完成**文本切片 → 向量化 → 向量入库**，提问时通过**相似度检索**召回相关片段，交给大模型做**检索增强生成（RAG）**，并以 SSE 流式返回带**引用溯源**的答案。

<p>
  <img alt="Java" src="https://img.shields.io/badge/Java-17-orange">
  <img alt="Spring Boot" src="https://img.shields.io/badge/SpringBoot-3.2.5-brightgreen">
  <img alt="MySQL" src="https://img.shields.io/badge/MySQL-8.0-blue">
  <img alt="Redis" src="https://img.shields.io/badge/Redis-5.0%2B-red">
  <img alt="Vue" src="https://img.shields.io/badge/Vue-3.4-42b883">
</p>

---

## 一、技术栈

| 层次 | 技术选型 |
|---|---|
| 语言与框架 | **Java 17**、**Spring Boot 3.2.5**、Maven |
| 数据访问 | MyBatis-Plus 3.5.7（分页插件、LambdaQueryWrapper 动态条件、聚合 SQL、批量插入） |
| 存储 | MySQL 8（业务数据 + **向量持久化**）、Redis（登录态、会话上下文、文档全文、切片向量缓存） |
| 文档解析 | Apache PDFBox 2.0.31（PDF 文本层抽取）、多字符集自动识别（TXT） |
| 大模型 | 任意 **OpenAI 兼容** chat/completions 接口（DeepSeek / 通义千问 / 智谱 GLM / OpenAI），JDK 17 `HttpClient` + SSE 流式解析 |
| 向量化 | 双模：OpenAI 兼容 `/embeddings` 语义向量 **或** 本地哈希向量（无外部依赖兜底） |
| 检索 | 余弦相似度 + 相似度阈值过滤 + TopK + 相邻片段合并 + 上下文预算控制 |
| 鉴权 | JWT（JJWT 0.11.5）+ 拦截器 + Redis 登录态比对 |
| 前端 | Vue 3.4（`<script setup>`）+ TypeScript + Element-Plus + Pinia + Vue Router + Axios + 原生 fetch 消费 SSE + ECharts |

---

## 二、核心功能

### 文档侧
| 功能 | 说明 |
|---|---|
| 文档上传 | PDF / TXT 直传（≤2MB）与大文件**分片上传**（MD5 秒传、断点续传、服务端分片完整性 + 合并 MD5 双重校验） |
| 文档解析 | PDFBox 抽取 PDF 文本层（含扫描件判定）、TXT 多字符集识别，解析状态机驱动（解析中 / 就绪 / 失败） |
| **文本切片** | 语义边界优先的切片策略，切片带序号与原文偏移量（见「项目亮点 ②」） |
| **向量入库** | 切片逐条向量化并以 JSON 形式持久化到 `doc_chunk` 表，同时缓存进 Redis |
| 文档管理 | 列表分页、按名称/类型/状态检索、删除（级联清理会话、消息、切片与缓存） |

### 问答侧
| 功能 | 说明 |
|---|---|
| **相似度检索** | 问题向量与切片向量逐一计算余弦相似度，召回 TopK |
| **检索增强问答** | 只把召回片段注入提示词（而非整篇文档），显著降低 token 成本并提升聚焦度 |
| **上下文多轮对话** | 多会话管理（新建 / 重命名 / 删除）、最近 6 条历史随上下文缓存滑动续期、历史消息分页 |
| **引用溯源** | 答案中的 `[n]` 标注与前端「引用来源」面板一一对应，可展开查看原文片段与相似度得分；来源随消息落库，**历史记录也能回显依据** |
| 流式输出 | SSE 打字机效果，事件：`session` / `sources` / `delta` / `done` / `error` |

### 管理端
用户管理（分页检索、启停、重置密码、级联删除）、数据看板（六大指标卡 + 近 7 日访问量/问答量趋势 + Top10 文档 + 访问日志）、全库文档检索与强删。

---

## 三、系统架构

```
┌────────────────────────── 前端 Vue3 + TS ──────────────────────────┐
│  登录/注册   我的文档(上传/分片/轮询)   问答工作台(流式/引用面板)   管理端  │
└───────────────────────────────┬────────────────────────────────────┘
                                │  REST (/api)  +  SSE (/api/chat/stream)
┌───────────────────────────────▼────────────────────────────────────┐
│                       Spring Boot 3 (JDK17)                        │
│                                                                    │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐            │
│  │  文档解析模块 │   │  向量检索模块 │   │ 大模型调用模块│            │
│  │ PdfTextParser│   │ TextChunker  │   │  LlmClient   │            │
│  │ TxtTextParser│──▶│ EmbeddingModel│──▶│PromptAssembler│           │
│  │ ChunkIndexSvc│   │ Retriever    │   │ (SSE 流式)   │            │
│  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘            │
│         │                  │                  │                    │
│         └──────────────────┼──────────────────┘                    │
│                     ┌──────▼────────┐                              │
│                     │  对话管理模块  │  ChatService                 │
│                     │ 会话/消息/上下文│  (两段式 prepare + stream)   │
│                     └──────┬────────┘                              │
└────────────────────────────┼───────────────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
   ┌─────────┐         ┌──────────┐        ┌──────────────┐
   │ MySQL 8 │         │  Redis   │        │ LLM / Embed  │
   │ 业务数据 │        │ 缓存/登录态│        │  OpenAI 兼容  │
   │ 切片向量 │        │ 上下文/向量│       │    HTTP API  │
   └─────────┘         └──────────┘        └──────────────┘
```

### 模块职责

| 模块 | 关键类 | 职责 |
|---|---|---|
| **文档解析模块** | `parser/PdfTextParser`、`parser/TxtTextParser`、`rag/TextChunker`、`rag/ChunkIndexService` | 抽取文档全文 → 按语义边界切片 → 逐片向量化 → 批量落库并写缓存；文档删除时级联清理切片 |
| **向量检索模块** | `ai/EmbeddingModel`（`ApiEmbeddingModel` / `LocalEmbeddingModel`）、`rag/Retriever`、`rag/VectorMath` | 向量化切片与问题，余弦召回 + 阈值过滤 + TopK + 相邻合并 + 上下文预算裁剪，输出带编号的引用片段 |
| **大模型调用模块** | `ai/LlmClient`、`ai/PromptAssembler` | 组装「系统指令 + 带编号参考资料 + 多轮历史 + 当前问题」，调用 OpenAI 兼容接口并逐行解析 SSE 增量 |
| **对话管理模块** | `service/impl/ChatServiceImpl`、`interceptor/AuthInterceptor`、`util/RedisUtil` | 会话与消息 CRUD、两段式流式问答（`prepare` 校验落库 / `streamAnswer` 流式转发）、Redis 上下文滑动续期、引用来源落库与回显 |

---

## 四、项目亮点（面试口述版）

### ① 解决大模型幻觉 —— 四道闸门，层层收紧

> 「大模型在私有文档问答里最大的风险是**一本正经地编造**。我做了四道闸门：」

1. **检索端过滤**：相似度低于阈值（默认 0.25）的片段直接丢弃，宁缺毋滥；
2. **零召回拒答**：检索结果为空时**根本不调用大模型**，直接返回固定文案（`refuse-when-empty`）——
   这是从源头杜绝无依据生成，比事后纠正更彻底；
3. **提示词约束**：明确划定知识边界（禁止使用参考资料外的知识）+ **允许弃答**
   （资料不足时必须回答「文档中未找到相关信息」）—— 给模型一条「不回答」的出路，
   是降低编造率最有效的一招；
4. **强制引用 + 溯源核对**：要求每处结论标注 `[n]`，前端把编号对应的原文片段展示出来，
   用户可**逐句核对**答案是否真有依据。

### ② 文档分块策略优化 —— 不让切片切断语义

> 「一开始我按固定长度切，效果很差：『系统支持 PDF 和 | TXT 两种格式』被从中间切开，
> 两片的语义都不完整，用户问『支持什么格式』时**两片都召不回来**。
> 后来改成**语义边界优先 + 定长兜底**：」

- 从切点向前**回溯寻找语义边界**，优先级依次为：段落分隔 → 换行 → 句末标点（`。！？`）→ 子句标点（`，、`）→ 空白；
- 窗口下限取「起点 + 半片长」，避免在开头附近切断产生大量碎片；
- 所有边界都没命中（如超长无标点串）才硬切，**保证不漏内容**；
- 相邻切片保留 **overlap 重叠窗口**（默认 80 字），防止语义恰好跨在切点上时两侧都丢。

> 「这套策略有单元测试兜底：断言切片必须**连续覆盖全文无空洞**、**除末片外都落在语义边界**、
> 相邻片必须有重叠 —— 改切片参数时不会悄悄劣化。」

### ③ 检索结果过滤 —— 召回只是第一步，过滤才决定质量

> 「向量召回出来的 TopK 不能直接用，我做了三层后处理：」

1. **相似度阈值过滤**：低于阈值的直接丢，避免把无关内容喂给模型「带偏」答案；
2. **相邻片段合并**：命中的切片若在原文档中相邻，合并为一个片段并取组内最高分 ——
   一段完整内容被切成三片且都被召回时，只占**一个引用编号**，既省上下文又保证语义完整；
3. **上下文预算控制**：按文档顺序累加，总量不超过 `max-context-chars`，
   防止超长文档挤爆上下文窗口；若单片本身就超预算则截断保留，保证至少有一份参考。

> 「另外一个工程细节：**问题向量与切片向量必须处于同一向量空间**。
> 我在 `EmbeddingModel` 接口上强制约定实现必须**无状态**；
> 并且在检索时校验维度一致性，检出「切换了 Embedding 模型但切片向量没重建」的情况并告警，
> 否则相似度会静默失效 —— 这类问题不校验的话很难发现。」

### ④ 无外部依赖也能跑通全链路

> 「Embedding 服务不像 chat 接口那样随手可得，DeepSeek 就没有 embeddings 接口。
> 所以我做了**双模向量化**：配置了 `aidoc.llm.embedding.api-key` 就用语义向量；
> 没配置则自动降级为**本地哈希向量**（字符二元组 + Hashing Trick + 亚线性词频 + L2 归一化）。
> 好处是项目 clone 下来**零额外成本就能跑通完整的 RAG 链路**，面试演示不会卡在缺 Key 上。」

### ⑤ 其他工程实践

- **两段式流式问答**：`prepare` 阶段在创建 `SseEmitter` **之前**完成全部校验与落库，
  失败时前端拿到的是标准 JSON 错误体而非半截流；
- **JWT + Redis 单点登录态**：注销 / 禁用即踢下线，token 无法继续使用；
- **缓存分层**：登录态、会话上下文（滑动续期）、文档全文、切片向量四类缓存，各自独立 TTL；
- **批量插入优化**：切片用单条多值 INSERT 分批落库，避免数百次数据库往返；
- **`@Async` 异步访问日志**：不阻塞主请求链路。

---

## 五、环境要求

| 软件 | 版本 | 说明 |
|---|---|---|
| JDK | **17**（必需） | pom 已锁定 `java.version=17` |
| Maven | 3.6+ | 项目使用 Maven 构建 |
| MySQL | **8.0+** | 执行 `sql/init.sql` 建库建表（含切片表） |
| Redis | 5.0+ | 默认 `localhost:6379` |
| Node.js | 18+ | 前端构建 |
| 大模型 API | 任意 **OpenAI 兼容**接口 | 必填 |
| Embedding API | 任意 **OpenAI 兼容** `/embeddings` | **选填**，不填自动用本地向量 |

---

## 六、启动步骤

### 1. 初始化数据库

```bash
mysql -uroot -p < sql/init.sql
```

创建数据库 `ai_doc_qa` 及 6 张表（`sys_user`、`doc_document`、`doc_chunk`、`chat_session`、`chat_message`、`access_log`）。

### 2. 启动 Redis

```bash
redis-server            # 默认 6379
```

### 3. 配置环境变量

> ⚠️ **所有密钥一律通过环境变量注入，仓库中不含任何明文密钥。**

复制根目录 `.env.example` 并填入真实值：

```bash
cp .env.example .env
```

**必填项：**

| 变量 | 说明 |
|---|---|
| `MYSQL_PASSWORD` | MySQL 密码（用户名默认 `root`，可用 `MYSQL_USERNAME` 覆盖） |
| `AIDOC_JWT_SECRET` | JWT 签名密钥，**长度必须 ≥ 32 字符**，可用 `openssl rand -base64 48` 生成 |
| `AIDOC_LLM_API_KEY` | 大模型 API Key |

**选填项：**

| 变量 | 说明 |
|---|---|
| `AIDOC_LLM_BASE_URL` / `AIDOC_LLM_MODEL` | 默认 `https://api.deepseek.com/v1` + `deepseek-chat` |
| `AIDOC_EMBEDDING_BASE_URL` / `AIDOC_EMBEDDING_MODEL` / `AIDOC_EMBEDDING_API_KEY` | 留空则使用**本地哈希向量**，无需申请任何服务 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | 默认 `localhost:6379` 无密码 |
| `AIDOC_ADMIN_PASSWORD` / `AIDOC_DEMO_PASSWORD` | 内置演示账号密码 |

> 使用 IntelliJ IDEA 的，也可以复制 `backend/src/main/resources/application-local.yml.example`
> 为 `application-local.yml` 填写配置，并以 `local` profile 启动。

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

> 也可打包运行：`mvn clean package -DskipTests && java -jar target/ai-doc-qa.jar`
>
> 首次启动自动创建内置账号（见 `application.yml` 的 `aidoc.init` 段）：
> **管理员 `admin / admin123`**、**演示用户 `demo / demo123`**（生产环境请通过环境变量覆盖）。

服务端口 **8080**，接口前缀 `/api`。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 **http://localhost:5173**（vite 已配置 `/api` 代理到 8080）。

生产构建：`npm run build`，将 `dist/` 交给 Nginx 并反向代理 `/api` 到后端。

### 6. 运行测试

```bash
cd backend
mvn test          # 含切片策略与检索算法的单元测试
```

---

## 七、验证流程（自测清单）

1. `demo / demo123` 登录 → **我的文档** → 上传一份 PDF 或 TXT；
2. 列表状态变为**就绪**（解析中的文档会自动轮询刷新，无需手动刷新页面）；
3. → **问答工作台** → 选择该文档 → 提问，观察：
   - 流式打字机输出；
   - 答案中的 `[n]` 标注；
   - 气泡下方 **「N 条引用来源」** 面板，展开可看到原文片段与相似度得分；
4. 尝试提问文档**未覆盖**的内容 → 应回答「文档中未找到相关信息」（零召回拒答，不会编造）；
5. 新建 / 重命名 / 删除会话，历史记录「加载更早消息」，**翻看历史消息时引用来源仍在**；
6. `admin / admin123` 登录 → 数据看板 / 用户管理 / 文档统计。

---

## 八、接口说明

### 通用约定

- 统一前缀 `/api`，统一返回体（HTTP 200 + body.code）：

```json
{ "code": 200, "message": "success", "data": {} }
```

- 鉴权头：`Authorization: Bearer {token}`
- 分页参数：`current`（默认 1）、`size`（默认 10，上限 100）
- 所有业务接口做**资源归属校验**（访问他人文档 / 会话返回 `FORBIDDEN`）
- 分页返回体：

```json
{ "records": [], "total": 0, "current": 1, "size": 10, "pages": 0 }
```

### 认证 `/api/auth`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册（拦截器白名单，无需 token） |
| POST | `/api/auth/login` | 登录，返回 token + 用户信息（白名单） |
| POST | `/api/auth/logout` | 注销（删除 Redis 登录态，token 即刻失效） |

### 用户 `/api/user`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/user/info` | 当前用户信息 |
| PUT | `/api/user/profile` | 修改昵称 / 邮箱 / 头像 |
| PUT | `/api/user/password` | 修改密码 |

### 文档 `/api/doc`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/doc/page` | 分页查询（`keyword` / `fileType` / `status`） |
| GET | `/api/doc/{id}` | 文档详情 |
| DELETE | `/api/doc/{id}` | 删除文档（级联清理会话、消息、切片、文件、缓存） |
| POST | `/api/doc/upload` | 小文件直传（`multipart/form-data`） |
| GET | `/api/doc/chunk/status` | 查询分片任务已上传序号（断点续传 + 秒传判断） |
| POST | `/api/doc/chunk/upload` | 上传单个分片 |
| POST | `/api/doc/chunk/merge` | 合并分片（完整性 + MD5 双校验后触发解析与建索引） |

> 文档状态：`0` 解析中 / `1` 就绪 / `2` 失败（失败原因见 `failReason`）。
> 只有**就绪**的文档才能用于问答。

### 问答 `/api/chat`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/chat/session/page` | 会话分页 |
| POST | `/api/chat/session` | 新建会话（绑定文档） |
| PUT | `/api/chat/session/{id}` | 重命名会话 |
| DELETE | `/api/chat/session/{id}` | 删除会话（级联消息与上下文缓存） |
| GET | `/api/chat/session/{id}/message/page` | 历史消息分页（`current=1` 为最新一页） |
| POST | `/api/chat/stream` | **流式问答**（SSE，见下） |

#### 流式问答协议 `/api/chat/stream`

请求（`application/json`）：

```json
{ "documentId": 1, "sessionId": 5, "question": "系统支持哪些文档格式？" }
```

> `sessionId` 传 `null` 时服务端自动新建会话，并通过 `session` 事件回传新 id。

响应（`text/event-stream`），每行 `data:` 为一条 JSON 事件：

| 事件 | 载荷 | 触发时机 |
|---|---|---|
| `session` | `{"event":"session","sessionId":12}` | 仅当服务端自动新建会话时 |
| `sources` | `{"event":"sources","sources":[{"refIndex":1,"chunkIndex":3,"score":0.71,"snippet":"...","charStart":0,"charEnd":500}]}` | 在正文输出前下发，无命中时为空数组 |
| `delta` | `{"event":"delta","content":"系统"}` | 每段增量内容 |
| `done` | `{"event":"done","messageId":88,"sessionId":12}` | 流结束 |
| `error` | `{"event":"error","message":"..."}` | 模型侧错误（已输出的内容不回滚） |

> **两段式设计**：`prepare` 阶段（校验文档归属、落库用户消息、检索、组装上下文）在创建
> `SseEmitter` **之前**完成，失败时前端收到的是标准 JSON 错误体，而不是半截流。

### 管理端 `/api/admin`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/user/page` | 用户分页（`keyword` / `role` / `status`） |
| POST | `/api/admin/user` | 新增用户 |
| PUT | `/api/admin/user/{id}/status` | 启用 / 禁用（禁用即强制下线） |
| PUT | `/api/admin/user/{id}/password` | 重置密码 |
| DELETE | `/api/admin/user/{id}` | 删除用户（级联清理其文档与切片） |
| GET | `/api/admin/doc/page` | 全库文档分页（跨用户） |
| DELETE | `/api/admin/doc/{id}` | 强制删除文档 |
| GET | `/api/admin/overview` | 数据看板（指标卡 + 7 日趋势 + Top10 文档） |
| GET | `/api/admin/access/page` | 访问日志分页 |

---

## 九、配置项说明

关键配置位于 `backend/src/main/resources/application.yml`：

```yaml
aidoc:
  rag:
    enabled: true              # 关闭则回退为「整篇文档注入」（可用于对比 RAG 效果）
    chunk-size: 500            # 切片目标字符数
    chunk-overlap: 80          # 相邻切片重叠字符数
    top-k: 4                   # 召回片段数上限
    score-threshold: 0.25      # 相似度阈值，低于则丢弃
    merge-adjacent: true       # 相邻片段合并
    max-context-chars: 6000    # 参考资料总字符上限
    refuse-when-empty: true    # 零召回直接拒答（不调用大模型）
  llm:
    temperature: 0.3           # 文档问答取低温度，保证忠实
    embedding:
      dimension: 512           # 本地向量维度
```

---

## 十、目录结构

```
ai-doc-qa/
├── .env.example                        # 环境变量模板（真实 .env 已被忽略）
├── sql/init.sql                        # 建库建表脚本（6 张表）
├── backend/
│   ├── pom.xml                         # JDK17 锁定
│   └── src/
│       ├── main/java/com/aidoc/
│       │   ├── ai/          # LlmClient、EmbeddingModel(双模)、PromptAssembler
│       │   ├── rag/         # TextChunker、Retriever、ChunkIndexService、VectorMath
│       │   ├── parser/      # PDF / TXT 解析
│       │   ├── controller/  # 五组 REST 接口
│       │   ├── service/     # 业务接口与实现
│       │   ├── mapper/      # MyBatis-Plus Mapper
│       │   ├── entity/      # 数据库实体
│       │   ├── dto/ vo/     # 入参与出参对象
│       │   ├── config/      # 装配配置（含向量的双模选择）
│       │   ├── interceptor/ # JWT 鉴权拦截器
│       │   ├── common/      # 统一返回体与全局异常处理
│       │   ├── util/        # JWT / Redis / 路径工具
│       │   ├── runner/      # 启动初始化内置账号
│       │   └── task/        # 定时清理临时文件
│       ├── main/resources/application.yml
│       └── test/java/com/aidoc/rag/    # 切片与检索的单元测试
└── frontend/
    └── src/
        ├── api/             # 接口封装与类型定义
        ├── utils/           # axios 封装、SSE 消费、本地存储
        ├── store/ router/   # 状态管理与路由守卫
        └── views/
            ├── login/
            ├── user/        # 我的文档、问答工作台
            └── admin/       # 数据看板、用户管理、文档统计
```

---

## 十一、常见问题

| 现象 | 排查方向 |
|---|---|
| 启动报「未配置 JWT 密钥」 | 未设置 `AIDOC_JWT_SECRET`，须 ≥ 32 字符 |
| 启动报 Redis 连接失败 | 确认 Redis 已启动（`redis-server`） |
| 前端请求跨域 | 开发环境走 vite 代理，无需额外配置 |
| 问答返回「尚未配置大模型 API Key」 | 未设置 `AIDOC_LLM_API_KEY` |
| 问答总是回答「文档中未找到相关信息」 | ① 文档是否已「就绪」；② 问题与文档内容确实无关；③ 可调低 `aidoc.rag.score-threshold` 观察召回变化 |
| 日志出现「切片向量维度与当前模型不一致」 | 中途切换过 Embedding 模型，**重新上传或重新解析该文档**即可重建向量 |
| 文档解析失败 | 查看列表的失败原因提示（如扫描件 PDF 无可提取文本层） |

---

## 十二、说明：为什么没有引入专业向量库

本项目**没有**使用 Milvus / pgvector / FAISS 等专业向量库，这是一个有意的工程取舍：

1. **检索范围天然很小**：会话绑定单个文档，检索只在**该文档的几百个切片**内进行，
   内存中做线性扫描 + 余弦计算即可达到**毫秒级**，引入 ANN 索引收益近乎为零；
2. **部署成本**：向量库会额外引入一个需要运维的中间件，对一个文档级问答系统是不成比例的负担；
3. **可替换性**：`Retriever` 只依赖「一批带向量的切片」这一抽象，
   若切片规模上升到百万级，只需把 `doc_chunk` 表替换为向量库并保留同一套检索接口即可。

> 若面试被问到「为什么不用向量数据库」，可以照此回答 ——
> **技术选型要匹配数据规模，而不是堆砌中间件。**
