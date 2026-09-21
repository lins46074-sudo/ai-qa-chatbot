package com.aidoc.log;

import com.aidoc.entity.AccessLog;
import com.aidoc.mapper.AccessLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 访问日志异步落库组件。
 *
 * <p>被 {@link com.aidoc.interceptor.AuthInterceptor#afterCompletion} 调用；
 * 方法标注 {@link Async}，由 Spring 异步线程池执行，不阻塞请求线程。
 * 内部吞掉一切异常仅 warn —— 日志写入失败绝不影响业务主流程。
 * 方法签名与 AuthInterceptor 调用处的参数一一对应。</p>
 */
@Slf4j
@Component
public class AccessLogRecorder {

    /** 访问日志 Mapper */
    private final AccessLogMapper accessLogMapper;

    /**
     * 构造注入访问日志 Mapper。
     *
     * @param accessLogMapper 访问日志 Mapper
     */
    public AccessLogRecorder(AccessLogMapper accessLogMapper) {
        this.accessLogMapper = accessLogMapper;
    }

    /**
     * 异步记录一条接口访问日志（字段与 access_log 表一一对应）。
     *
     * @param userId     用户 ID（未登录为 null，此时 username 为空串）
     * @param username   用户名（未登录为空串）
     * @param ip         客户端 IP
     * @param path       请求路径
     * @param method     HTTP 方法
     * @param httpStatus 响应码
     * @param costMs     耗时毫秒
     */
    @Async
    public void recordAsync(Long userId, String username, String ip,
                            String path, String method, int httpStatus, long costMs) {
        try {
            AccessLog accessLog = new AccessLog();
            accessLog.setUserId(userId);
            accessLog.setUsername(username == null ? "" : username);
            accessLog.setIp(ip == null ? "" : ip);
            accessLog.setPath(path);
            accessLog.setMethod(method);
            accessLog.setHttpStatus(httpStatus);
            accessLog.setCostMs(costMs);
            // create_time 由数据库默认值 CURRENT_TIMESTAMP 填充
            accessLogMapper.insert(accessLog);
        } catch (Exception e) {
            // 日志落库失败不影响主流程：仅记录 warn
            log.warn("[访问日志] 异步落库失败: method={}, path={}", method, path, e);
        }
    }
}
