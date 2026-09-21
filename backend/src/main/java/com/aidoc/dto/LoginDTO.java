package com.aidoc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO（POST /api/auth/login）。
 *
 * <p>loginType 用于区分登录入口：USER 普通用户端、ADMIN 管理端。
 * 管理端登录时后端会校验账号角色必须为 ADMIN，避免普通用户从管理员入口登录。</p>
 */
@Data
public class LoginDTO {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（明文，后端用 BCrypt 比对密文） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 登录入口类型：USER（用户端，默认）/ ADMIN（管理端） */
    private String loginType;
}
