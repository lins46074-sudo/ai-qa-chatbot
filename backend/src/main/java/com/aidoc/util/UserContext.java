package com.aidoc.util;

/**
 * 当前登录用户上下文（基于 ThreadLocal 静态持有）。
 *
 * <p>AuthInterceptor 在 preHandle 校验通过后调用 {@link #set(LoginUser)} 填充，
 * 业务代码（Service / Controller）通过 {@link #currentUserId()} 便捷获取当前用户 ID；
 * 请求结束（afterCompletion）必须调用 {@link #clear()}，防止 Tomcat 线程池复用导致用户串号。</p>
 */
public class UserContext {

    /** 线程本地变量：保存当前请求线程中的登录用户（LoginUser 定义于本包） */
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    /**
     * 私有构造器：纯静态工具类，禁止实例化。
     */
    private UserContext() {
    }

    /**
     * 设置当前请求的登录用户。
     *
     * @param user 登录用户（含 userId / username / role）
     */
    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /**
     * 获取当前请求的登录用户。
     *
     * @return 登录用户；未登录时返回 null
     */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 清除当前线程的登录用户（请求结束时必须调用，防止线程复用串号）。
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 便捷方法：获取当前登录用户 ID。
     *
     * @return 当前用户 ID；未登录时返回 null
     */
    public static Long currentUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }
}
