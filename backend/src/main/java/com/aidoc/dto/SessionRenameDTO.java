package com.aidoc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会话重命名请求 DTO（PUT /api/chat/session/{id}）。
 */
@Data
public class SessionRenameDTO {

    /** 新标题（必填，最长 100 字） */
    @NotBlank
    @Size(max = 100, message = "标题最长100字")
    private String title;
}
