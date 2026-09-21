-- =====================================================================
-- Java RAG 私有文档 AI 问答系统 · MySQL8 初始化脚本
-- 执行方式: mysql -uroot -p < init.sql
-- 说明: 初始账号(admin/admin123、demo/demo123)由后端启动时自动初始化
-- =====================================================================
CREATE DATABASE IF NOT EXISTS ai_doc_qa DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_doc_qa;

-- 用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username        VARCHAR(50)  NOT NULL COMMENT '登录名',
  password        VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  nickname        VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '昵称',
  email           VARCHAR(100) NOT NULL DEFAULT '' COMMENT '邮箱',
  avatar          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像URL',
  role            VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色 USER/ADMIN',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
  last_login_time DATETIME     NULL COMMENT '最近登录时间',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 文档表
DROP TABLE IF EXISTS doc_document;
CREATE TABLE doc_document (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT       NOT NULL COMMENT '所属用户ID',
  file_name     VARCHAR(255) NOT NULL COMMENT '原始文件名',
  original_path VARCHAR(500) NOT NULL DEFAULT '' COMMENT '原文件相对路径',
  text_path     VARCHAR(500) NOT NULL DEFAULT '' COMMENT '解析文本相对路径',
  file_size     BIGINT       NOT NULL DEFAULT 0 COMMENT '文件字节数',
  file_type     VARCHAR(20)  NOT NULL DEFAULT 'TXT' COMMENT '类型 PDF/TXT',
  page_count    INT          NOT NULL DEFAULT 0 COMMENT 'PDF页数(TXT为0)',
  char_count    INT          NOT NULL DEFAULT 0 COMMENT '解析文本字符数',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '解析状态 0解析中 1就绪 2失败',
  fail_reason   VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户文档表';

-- 会话表
DROP TABLE IF EXISTS chat_session;
CREATE TABLE chat_session (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT       NOT NULL COMMENT '所属用户ID',
  document_id   BIGINT       NOT NULL COMMENT '关联文档ID',
  title         VARCHAR(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  message_count INT          NOT NULL DEFAULT 0 COMMENT '消息条数',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答会话表';

-- 消息表
DROP TABLE IF EXISTS chat_message;
CREATE TABLE chat_message (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  session_id  BIGINT       NOT NULL COMMENT '会话ID',
  role        VARCHAR(10)  NOT NULL COMMENT '角色 USER/ASSISTANT',
  content     LONGTEXT     NOT NULL COMMENT '消息内容',
  sources     MEDIUMTEXT   NULL COMMENT '引用来源(JSON数组,仅ASSISTANT消息有值)',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答消息表';

-- 文档切片表（RAG 检索的最小单元）
DROP TABLE IF EXISTS doc_chunk;
CREATE TABLE doc_chunk (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  doc_id      BIGINT       NOT NULL COMMENT '所属文档ID',
  chunk_index INT          NOT NULL DEFAULT 0 COMMENT '切片在文档内的序号(0起)',
  content     TEXT         NOT NULL COMMENT '切片正文',
  char_start  INT          NOT NULL DEFAULT 0 COMMENT '在规范化文本中的起始下标',
  char_end    INT          NOT NULL DEFAULT 0 COMMENT '在规范化文本中的结束下标(不含)',
  embedding   MEDIUMTEXT   NOT NULL COMMENT '切片向量(JSON数组字符串)',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_doc_id (doc_id, chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档切片表(向量检索数据源)';

-- 访问日志表（管理端访问量看板数据源）
DROP TABLE IF EXISTS access_log;
CREATE TABLE access_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT       NULL COMMENT '用户ID(未登录为NULL)',
  username    VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '用户名',
  ip          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '客户端IP',
  path        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '请求路径',
  method      VARCHAR(10)  NOT NULL DEFAULT '' COMMENT 'HTTP方法',
  http_status INT          NOT NULL DEFAULT 200 COMMENT '响应码',
  cost_ms     BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_create_time (create_time),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口访问日志表';
