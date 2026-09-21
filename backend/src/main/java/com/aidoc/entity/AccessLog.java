package com.aidoc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口访问日志实体，对应表 access_log。
 *
 * <p>由 AuthInterceptor 在请求结束后异步记录（见 AccessLogRecorder），
 * 是管理端"访问量数据看板"（近 7 日趋势等）的数据源。
 * 注意：本表无 update_time 字段。</p>
 */
@Data
@TableName("access_log")
public class AccessLog {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID（未登录为 NULL，索引 idx_user_id） */
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

    /** 创建时间（DB 默认 CURRENT_TIMESTAMP，索引 idx_create_time） */
    private LocalDateTime createTime;
}
