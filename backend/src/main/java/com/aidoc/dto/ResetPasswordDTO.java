package com.aidoc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置用户密码请求 DTO（PUT /api/admin/user/{id}/password）。
 */
@Data
public class ResetPasswordDTO {

    /** 新密码：6-32 位（存库前 BCrypt 加密） */
    @NotBlank
    @Size(min = 6, max = 32, message = "新密码长度须为6-32位")
    private String newPassword;
}
