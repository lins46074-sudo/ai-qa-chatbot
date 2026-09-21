package com.aidoc.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>Service 层校验失败 / 业务规则不满足时抛出本异常，由
 * {@link GlobalExceptionHandler} 统一捕获并转换为 Result 返回。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码（默认取 ResultCode.BUSINESS_ERROR 的 1000） */
    private final int code;

    /**
     * 按响应码枚举构造异常。
     *
     * @param rc 响应码枚举（携带 code 与 message）
     */
    public BusinessException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }

    /**
     * 自定义状态码与提示信息。
     *
     * @param code    状态码
     * @param message 提示信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 仅给提示信息，状态码默认 BUSINESS_ERROR(1000)。
     *
     * @param message 提示信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }
}
