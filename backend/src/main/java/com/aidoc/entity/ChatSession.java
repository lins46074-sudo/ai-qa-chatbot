package com.aidoc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答会话实体，对应表 chat_session。
 *
 * <p>一个会话关联一个文档（document_id），其下挂多条 chat_message。</p>
 */
@Data
@TableName("chat_session")
public class ChatSession {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID（索引 idx_user_id） */
    private Long userId;

    /** 关联文档 ID（索引 idx_document_id） */
    private Long documentId;

    /** 会话标题（默认「新对话」，自动建会话时取问题前 15 字） */
    private String title;

    /** 消息条数 */
    private Integer messageCount;

    /** 创建时间（DB 默认 CURRENT_TIMESTAMP） */
    private LocalDateTime createTime;

    /** 更新时间（DB 自动 ON UPDATE） */
    private LocalDateTime updateTime;
}
