package com.aidoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置类。
 *
 * <p>使用 spring-security-crypto 提供的 {@link BCryptPasswordEncoder}
 * 对用户密码进行 BCrypt 加盐哈希（仅引入加密库，不引入完整 Spring Security 过滤链），
 * 供认证模块注册 / 登录 / 改密时校验使用。</p>
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * 注册 BCrypt 密码编码器 Bean。
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
