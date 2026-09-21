package com.aidoc.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端用户列表 VO（GET /api/admin/user/page）。
 *
 * <p>在 UserVO 基础上扩展文档数 / 会话数（管理模块分页查用户后两次聚合回填，避免 N+1）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserVO extends UserVO {

    /** 该用户拥有的文档数 */
    private Integer docCount;

    /** 该用户拥有的会话数 */
    private Integer sessionCount;
}
