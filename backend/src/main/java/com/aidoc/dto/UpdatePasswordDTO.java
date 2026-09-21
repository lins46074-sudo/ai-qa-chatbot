package com.aidoc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求 DTO（PUT /api/user/password）。
 *
 * <p>修改前须校验旧密码与当前用户匹配；新密码存库前 BCrypt 加密。</p>
 */
@Data
public class UpdatePasswordDTO {

    /** 旧密码（须与库中 BCrypt 密文比对一致） */
    @NotBlank
    private String oldPassword;

    /** 新密码：6-32 位 */
    @NotBlank
    @Size(min = 6, max = 32, message = "新密码长度须为6-32位")
    private String newPassword;
}
