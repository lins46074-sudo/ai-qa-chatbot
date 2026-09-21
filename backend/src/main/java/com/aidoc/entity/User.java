package com.aidoc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 sys_user。
 *
 * <p>字段与 sql/init.sql 中的建表语句一一对应；
 * create_time / update_time 由数据库默认值填充（插入策略 NOT_NULL 自动省略 null 字段，无需自动填充处理器）。</p>
 */
@Data
@TableName("sys_user")
public class User {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录名（唯一键 uk_username） */
    private String username;

    /** BCrypt 密文 */
    private String password;

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

    /** 创建时间（DB 默认 CURRENT_TIMESTAMP） */
    private LocalDateTime createTime;

    /** 更新时间（DB 自动 ON UPDATE） */
    private LocalDateTime updateTime;
}
