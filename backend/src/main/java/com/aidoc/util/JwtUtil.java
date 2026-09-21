package com.aidoc.util;

import com.aidoc.common.BusinessException;
import com.aidoc.common.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类。
 *
 * <p>读取 application.yml 中 aidoc.jwt.* 配置：
 * <ul>
 *   <li>secret：HS256 签名密钥（长度必须 ≥ 32 字符）；</li>
 *   <li>expire-hours：token 有效期（小时）。</li>
 * </ul>
 * token 的 subject 存放 userId，并附带 username / role 两个自定义声明，
 * 供 AuthInterceptor 校验后填充 UserContext 使用。</p>
 */
@Component
public class JwtUtil {

    /** HS256 签名密钥对象（由配置中的 secret 派生） */
    private final SecretKey key;

    /** token 有效期（小时，来自 aidoc.jwt.expire-hours） */
    private final long expireHours;

    /**
     * 构造器：从配置注入密钥与有效期，并派生 HMAC 密钥对象。
     *
     * @param secret      签名密钥明文（aidoc.jwt.secret）
     * @param expireHours 有效期小时数（aidoc.jwt.expire-hours）
     */
    public JwtUtil(@Value("${aidoc.jwt.secret}") String secret,
                   @Value("${aidoc.jwt.expire-hours}") long expireHours) {
        // 启动期前置校验：密钥缺失或过短时给出可读提示，而不是抛出晦涩的 WeakKeyException。
        // 密钥通过环境变量 AIDOC_JWT_SECRET 注入，见 README「配置说明」。
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "未配置 JWT 密钥：请设置环境变量 AIDOC_JWT_SECRET（长度 >= 32 字符）");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT 密钥过短：AIDOC_JWT_SECRET 当前 " + keyBytes.length
                            + " 字节，HS256 要求至少 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expireHours = expireHours;
    }

    /**
     * 生成 JWT token：subject = userId，声明 username / role，签名算法 HS256。
     *
     * @param userId   用户 ID（写入 sub）
     * @param username 用户名（写入 username 声明）
     * @param role     角色（USER/ADMIN，写入 role 声明）
     * @return 签名后的 JWT 字符串
     */
    public String createToken(Long userId, String username, String role) {
        Date now = new Date();
        // 过期时间 = 当前时间 + expire-hours 小时
        Date expiration = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析并校验 token，返回其 Claims 负载。
     *
     * <p>token 被篡改、签名不合法或已过期时抛出
     * {@link BusinessException}{@code (UNAUTHORIZED)}，由全局异常处理器 / 拦截器转为 401。</p>
     *
     * @param token JWT 字符串
     * @return 解析出的 Claims（含 sub / username / role）
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException：签名错误 / 过期等；IllegalArgumentException：token 为 null 或格式非法
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }
}
