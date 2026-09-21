package com.aidoc.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建会话请求 DTO（POST /api/chat/session）。
 */
@Data
public class SessionCreateDTO {

    /** 关联文档 ID（必填） */
    @NotNull(message = "请选择关联文档")
    private Long documentId;

    /** 会话标题（可空，缺省使用默认标题「新对话」） */
    @Size(max = 100)
    private String title;
}
