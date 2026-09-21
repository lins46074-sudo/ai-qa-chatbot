package com.aidoc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答消息实体，对应表 chat_message。
 *
 * <p>注意：本表无 update_time 字段（消息一经写入不再修改）；
 * role 取值 USER / ASSISTANT。</p>
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID（索引 idx_session_id） */
    private Long sessionId;

    /** 角色 USER/ASSISTANT */
    private String role;

    /** 消息内容（LONGTEXT） */
    private String content;

    /**
     * 引用来源（JSON 数组字符串，仅 ASSISTANT 消息有值）。
     *
     * <p>记录该条回答所依据的文档片段，使<b>历史消息也能回显检索来源</b>；
     * 若只在前端实时保存，用户刷新或翻看历史时会丢失依据，无法核对答案。</p>
     */
    private String sources;

    /** 创建时间（DB 默认 CURRENT_TIMESTAMP） */
    private LocalDateTime createTime;
}
