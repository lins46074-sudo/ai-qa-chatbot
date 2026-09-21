package com.aidoc.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息 VO（当前用户信息 / 个人中心展示用）。
 *
 * <p>注意：严禁把 password 等敏感字段放入 VO 返回前端。</p>
 */
@Data
public class UserVO {

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 头像 URL */
    private String avatar;

    /** 角色 USER/ADMIN */
    private String role;

    /** 状态 1正常 0禁用 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    /** 注册时间 */
    private LocalDateTime createTime;
}
