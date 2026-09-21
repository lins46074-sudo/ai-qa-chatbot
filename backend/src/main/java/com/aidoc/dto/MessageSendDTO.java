package com.aidoc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送问答消息请求 DTO（POST /api/chat/stream，SSE 流式）。
 *
 * <p>sessionId 可空：为空时后端自动创建新会话（标题取问题前 15 字），
 * 并在首条 SSE 事件中回传 sessionId。</p>
 */
@Data
public class MessageSendDTO {

    /** 提问所基于的文档 ID（必填，须归属当前用户） */
    @NotNull
    private Long documentId;

    /** 会话 ID（可空，空则自动建会话） */
    private Long sessionId;

    /** 问题内容（必填，最长 4000 字） */
    @NotBlank(message = "问题不能为空")
    @Size(max = 4000, message = "问题最长4000字")
    private String question;
}
