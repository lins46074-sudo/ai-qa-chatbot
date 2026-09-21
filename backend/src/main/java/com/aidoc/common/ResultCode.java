package com.aidoc.common;

import lombok.Getter;

/**
 * 统一响应码枚举。
 *
 * <p>前后端约定：HTTP 状态码一律 200，业务结果以 body.code 区分；
 * 前端 request 拦截器据此判断成功 / 弹错 / 跳登录。</p>
 */
@Getter
public enum ResultCode {

    /** 操作成功 */
    SUCCESS(200, "操作成功"),
    /** 请求参数错误（参数缺失 / 格式错误 / 校验失败） */
    BAD_REQUEST(400, "请求参数错误"),
    /** 未登录或登录已过期（token 无效 / Redis 登录态被踢） */
    UNAUTHORIZED(401, "未登录或登录已过期"),
    /** 无权访问该资源（角色不足或非资源归属人） */
    FORBIDDEN(403, "无权访问该资源"),
    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 通用业务处理失败 */
    BUSINESS_ERROR(1000, "业务处理失败"),
    /** 系统繁忙（未捕获异常统一兜底） */
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试");

    /** 状态码 */
    private final int code;

    /** 提示信息 */
    private final String message;

    /**
     * 枚举构造器。
     *
     * @param code    状态码
     * @param message 提示信息
     */
    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
