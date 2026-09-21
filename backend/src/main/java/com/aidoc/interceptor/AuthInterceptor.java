package com.aidoc.interceptor;

import com.aidoc.common.Result;
import com.aidoc.common.ResultCode;
import com.aidoc.log.AccessLogRecorder;
import com.aidoc.util.JwtUtil;
import com.aidoc.util.LoginUser;
import com.aidoc.util.RedisKeys;
import com.aidoc.util.RedisUtil;
import com.aidoc.util.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 认证拦截器（core 产物，供全部业务模块共用）。
 *
 * <p>执行流程（preHandle）：
 * <ol>
 *   <li>OPTIONS 预检直接放行；</li>
 *   <li>取请求头（aidoc.jwt.header），校验前缀（aidoc.jwt.prefix），缺失 / 格式错 → 401；</li>
 *   <li>JwtUtil 解析 token，非法 / 过期 → 401；</li>
 *   <li>Redis 登录态比对：key {@code aidoc:login:{userId}} 不存在或值不含该 token
 *       → 401（已注销 / 被踢 / 被顶号）；</li>
 *   <li>校验通过后填充 UserContext，并把 startTime / userId / username / ip 存入 request attribute
 *       （供 afterCompletion 计算耗时与异步记日志）；</li>
 *   <li>/api/admin/** 且角色非 ADMIN → 403。</li>
 * </ol>
 * afterCompletion：清理 UserContext（防线程复用串号），并调 AccessLogRecorder 异步落库访问日志。
 * 拒绝响应以注入的 ObjectMapper 输出统一 Result JSON。</p>
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** request attribute 键：请求开始时间戳 */
    private static final String ATTR_START_TIME = "auth_start_time";

    /** request attribute 键：通过校验的用户 ID */
    private static final String ATTR_USER_ID = "auth_user_id";

    /** request attribute 键：通过校验的用户名 */
    private static final String ATTR_USERNAME = "auth_username";

    /** request attribute 键：客户端 IP */
    private static final String ATTR_IP = "auth_ip";

    /** JWT 工具 */
    private final JwtUtil jwtUtil;

    /** Redis 操作工具 */
    private final RedisUtil redisUtil;

    /** Jackson 序列化器（输出拒绝 JSON） */
    private final ObjectMapper objectMapper;

    /** 访问日志异步落库组件 */
    private final AccessLogRecorder accessLogRecorder;

    /** 鉴权请求头名称（aidoc.jwt.header，默认 Authorization） */
    @Value("${aidoc.jwt.header}")
    private String headerName;

    /** token 前缀（aidoc.jwt.prefix，含空格，默认 "Bearer "） */
    @Value("${aidoc.jwt.prefix}")
    private String tokenPrefix;

    /**
     * 构造注入依赖。
     *
     * @param jwtUtil           JWT 工具
     * @param redisUtil         Redis 工具
     * @param objectMapper      Jackson 序列化器
     * @param accessLogRecorder 访问日志异步落库组件
     */
    public AuthInterceptor(JwtUtil jwtUtil, RedisUtil redisUtil,
                           ObjectMapper objectMapper, AccessLogRecorder accessLogRecorder) {
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
        this.objectMapper = objectMapper;
        this.accessLogRecorder = accessLogRecorder;
    }

    /**
     * 请求处理前：完成 JWT 校验、Redis 登录态比对、UserContext 填充与管理员权限校验。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  处理器
     * @return true 放行；false 拦截（已写出 401/403 JSON）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 跨域预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 2. 取请求头并校验前缀（缺失 / 格式错误 → 401）
        String authHeader = request.getHeader(headerName);
        if (authHeader == null || !authHeader.startsWith(tokenPrefix)) {
            writeJson(response, Result.error(ResultCode.UNAUTHORIZED));
            return false;
        }
        String token = authHeader.substring(tokenPrefix.length()).trim();

        // 3. 解析 JWT（签名非法 / 过期等由 JwtUtil 抛 BusinessException）并取载荷
        final Long userId;
        final String username;
        final String role;
        try {
            Claims claims = jwtUtil.parseToken(token);
            // sub 存放 userId；username / role 为自定义声明
            userId = Long.valueOf(claims.getSubject());
            username = claims.get("username", String.class);
            role = claims.get("role", String.class);
        } catch (Exception e) {
            writeJson(response, Result.error(ResultCode.UNAUTHORIZED));
            return false;
        }

        // 4. Redis 登录态比对：key aidoc:login:{userId} 须存在且值含该 token，
        //    否则视为已注销 / 被踢下线 / 被顶号 → 401
        String cachedLogin = redisUtil.getString(RedisKeys.LOGIN + userId);
        if (cachedLogin == null || !cachedLogin.contains(token)) {
            writeJson(response, Result.error(ResultCode.UNAUTHORIZED));
            return false;
        }

        // 5. 填充 UserContext（业务代码经 UserContext.currentUserId() 取当前用户）
        UserContext.set(new LoginUser(userId, username, role));

        // 6. 记录请求属性：开始时间 / 用户信息 / IP（供 afterCompletion 计算耗时与记日志）
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        request.setAttribute(ATTR_USER_ID, userId);
        request.setAttribute(ATTR_USERNAME, username);
        request.setAttribute(ATTR_IP, getClientIp(request));

        // 7. 管理端接口角色校验：/api/admin/** 仅 ADMIN 可访问
        if (request.getRequestURI().startsWith("/api/admin/") && !"ADMIN".equals(role)) {
            writeJson(response, Result.error(ResultCode.FORBIDDEN));
            return false;
        }
        return true;
    }

    /**
     * 请求处理完成后：先清理 UserContext（防止 Tomcat 线程池复用导致串号），
     * 再按 request attribute 中记录的起始时间与用户信息异步落库访问日志。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  处理器
     * @param ex       处理过程中抛出的异常（未抛则为 null）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 1. 无论成功失败，先清理当前线程的登录用户上下文
        UserContext.clear();

        // 2. 取 request attribute 组装日志参数并异步落库（失败仅 warn，不影响主流程）
        try {
            Object startAttr = request.getAttribute(ATTR_START_TIME);
            if (!(startAttr instanceof Long startTime)) {
                return;
            }
            long costMs = System.currentTimeMillis() - startTime;
            Long userId = (Long) request.getAttribute(ATTR_USER_ID);
            String username = (String) request.getAttribute(ATTR_USERNAME);
            String ip = (String) request.getAttribute(ATTR_IP);
            accessLogRecorder.recordAsync(userId, username, ip,
                    request.getRequestURI(), request.getMethod(),
                    response.getStatus(), costMs);
        } catch (Exception e) {
            log.warn("[访问日志] 记录异常: uri={}", request.getRequestURI(), e);
        }
    }

    /**
     * 拒绝请求：以统一 Result JSON 结构写出（HTTP 200 + body.code=401/403）。
     *
     * <p>说明：项目约定业务错误一律 HTTP 200 + body.code 承载，
     * 前端 request 拦截器据此弹错并处理 401 跳登录，故此处不写非 2xx 状态码。</p>
     *
     * @param response 当前响应
     * @param result   统一返回体
     */
    private void writeJson(HttpServletResponse response, Result<?> result) {
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), result);
        } catch (IOException e) {
            log.error("[认证拦截器] 写出拒绝响应失败", e);
        }
    }

    /**
     * 获取客户端 IP：优先取 X-Forwarded-For 首段（反向代理场景），否则取 remoteAddr。
     *
     * @param request 当前请求
     * @return 客户端 IP 字符串
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 代理链中第一个地址才是真实客户端
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
