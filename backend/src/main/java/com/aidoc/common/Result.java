package com.aidoc.common;

import lombok.Data;

/**
 * 统一接口返回体 Result&lt;T&gt;。
 *
 * <p>所有 REST 接口（SSE 流式除外）均返回该结构，由 Jackson 序列化为
 * {@code {"code":200,"message":"操作成功","data":...}} 形式。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    /** 状态码：200 成功，其余见 ResultCode */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据（成功时的负载，可为 null） */
    private T data;

    /**
     * 成功（无数据）：code=200，data=null。
     *
     * @param <T> 数据类型
     * @return 成功结果
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 成功（携带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data) {
        return ok(ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功（自定义提示并携带数据）。
     *
     * @param message 提示信息
     * @param data    业务数据
     * @param <T>     数据类型
     * @return 成功结果
     */
    public static <T> Result<T> ok(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    /**
     * 失败（按响应码枚举构造，data=null）。
     *
     * @param rc  响应码枚举
     * @param <T> 数据类型
     * @return 失败结果
     */
    public static <T> Result<T> error(ResultCode rc) {
        return error(rc.getCode(), rc.getMessage());
    }

    /**
     * 失败（自定义状态码与提示）。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 失败结果
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
