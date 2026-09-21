package com.aidoc.vo;

import lombok.Data;

/**
 * 登录成功返回 VO（POST /api/auth/login 的 data）。
 */
@Data
public class LoginVO {

    /** JWT token（请求时放入 Authorization: Bearer {token}） */
    private String token;

    /** token 类型，固定 "Bearer" */
    private String tokenType = "Bearer";

    /** 有效期（秒），由 jwt.expire-hours 换算 */
    private Long expiresIn;

    /** 当前登录用户信息 */
    private UserVO user;
}
