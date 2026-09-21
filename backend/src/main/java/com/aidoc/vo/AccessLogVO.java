package com.aidoc.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访问日志 VO（管理端 GET /api/admin/access/page）。
 */
@Data
public class AccessLogVO {

    /** 日志 ID */
    private Long id;

    /** 用户 ID（未登录为 NULL） */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 客户端 IP */
    private String ip;

    /** 请求路径 */
    private String path;

    /** HTTP 方法 */
    private String method;

    /** 响应码 */
    private Integer httpStatus;

    /** 耗时毫秒 */
    private Long costMs;

    /** 访问时间 */
    private LocalDateTime createTime;
}
