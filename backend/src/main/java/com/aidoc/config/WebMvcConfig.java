package com.aidoc.config;

import com.aidoc.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类。
 *
 * <p>注册认证拦截器与跨域规则：
 * <ul>
 *   <li>拦截 /api/** 全部后端接口，放行登录 / 注册两个匿名接口；</li>
 *   <li>跨域：允许任意来源、任意方法与请求头，不携带凭证，预检缓存 3600 秒。</li>
 * </ul>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 认证拦截器（core 产物，负责 JWT + Redis 登录态校验与访问日志记录） */
    private final AuthInterceptor authInterceptor;

    /**
     * 构造注入认证拦截器。
     *
     * @param authInterceptor 认证拦截器
     */
    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * 注册拦截器：拦截所有 /api/** 请求，放行登录、注册接口。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register");
    }

    /**
     * 配置全局跨域策略（前后端分离开发环境使用）。
     *
     * @param registry 跨域注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
