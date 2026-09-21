package com.aidoc.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录用户信息小类。
 *
 * <p>供 {@link UserContext}（ThreadLocal）存放当前请求的登录用户，
 * 由 AuthInterceptor 从 JWT claims 解析后填充；字段与 JWT 声明一一对应。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 角色（USER / ADMIN） */
    private String role;
}
