package com.aidoc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 DTO（POST /api/auth/register）。
 *
 * <p>nickname / email 可空；username 全局唯一（注册前查重 + DuplicateKey 兜底）。</p>
 */
@Data
public class RegisterDTO {

    /** 用户名：3-20 位字母数字下划线 */
    @NotBlank
    @Size(min = 3, max = 20, message = "用户名长度须为3-20位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅支持字母数字下划线")
    private String username;

    /** 密码：6-32 位（存库前 BCrypt 加密） */
    @NotBlank
    @Size(min = 6, max = 32, message = "密码长度须为6-32位")
    private String password;

    /** 昵称（可空，最长 20 字） */
    @Size(max = 20)
    private String nickname;

    /** 邮箱（可空，格式须合法） */
    @Email(message = "邮箱格式不正确")
    private String email;
}
