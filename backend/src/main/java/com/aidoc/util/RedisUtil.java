package com.aidoc.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类。
 *
 * <p>基于 {@link StringRedisTemplate} 以 JSON 字符串形式读写对象，
 * 序列化 / 反序列化交由注入的 {@link ObjectMapper} 完成。
 * <b>铁律</b>：每个方法内部 try/catch 缓存异常，仅 warn 日志、返回安全默认值，
 * 绝不把 Redis 故障传播到业务主流程。</p>
 */
@Slf4j
@Component
public class RedisUtil {

    /** Redis 字符串模板（key/value 均为 String） */
    private final StringRedisTemplate redisTemplate;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * 构造注入依赖。
     *
     * @param redisTemplate Redis 字符串模板
     * @param objectMapper  Jackson ObjectMapper
     */
    public RedisUtil(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 直接读取字符串值。
     *
     * @param key Redis 键
     * @return 字符串值；键不存在或异常时返回 null
     */
    public String getString(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[Redis] getString 失败, key={}", key, e);
            return null;
        }
    }

    /**
     * 读取 JSON 并反序列化为指定类型的对象。
     *
     * @param key   Redis 键
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 反序列化结果；键不存在 / 异常时返回 null
     */
    public <T> T get(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("[Redis] get 失败, key={}", key, e);
            return null;
        }
    }

    /**
     * 读取 JSON 数组并反序列化为指定类型的 List。
     *
     * @param key   Redis 键
     * @param clazz 元素类型
     * @param <T>   元素类型泛型
     * @return 反序列化结果列表；键不存在 / 异常时返回 null
     */
    public <T> List<T> getList(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            // 构造 List<T> 泛型类型用于反序列化
            JavaType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
            return objectMapper.readValue(json, javaType);
        } catch (Exception e) {
            log.warn("[Redis] getList 失败, key={}", key, e);
            return null;
        }
    }

    /**
     * 写入对象（自动 JSON 序列化），并设置过期时间。
     *
     * @param key     Redis 键
     * @param value   任意对象（null 时跳过写入）
     * @param timeout 过期时长
     * @param unit    过期时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        if (value == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, timeout, unit);
        } catch (Exception e) {
            log.warn("[Redis] set 失败, key={}", key, e);
        }
    }

    /**
     * 写入字符串值并设置过期时间。
     *
     * @param key     Redis 键
     * @param value   字符串值
     * @param timeout 过期时长
     * @param unit    过期时间单位
     */
    public void setString(String key, String value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.warn("[Redis] setString 失败, key={}", key, e);
        }
    }

    /**
     * 删除指定键。
     *
     * @param key Redis 键
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[Redis] delete 失败, key={}", key, e);
        }
    }

    /**
     * 判断键是否存在。
     *
     * @param key Redis 键
     * @return 是否存在；异常时返回 false
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("[Redis] hasKey 失败, key={}", key, e);
            return false;
        }
    }

    /**
     * 按前缀批量删除键（如清除某用户全部登录态 / 会话缓存）。
     *
     * @param prefix 键前缀，完整匹配键 = prefix + 业务后缀
     */
    public void deleteByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[Redis] deleteByPrefix 失败, prefix={}", prefix, e);
        }
    }

    /**
     * 仅当键不存在时写入（用于分布式防重 / 秒杀等场景）。
     *
     * @param key     Redis 键
     * @param value   任意对象（自动 JSON 序列化）
     * @param timeout 过期时长
     * @param unit    过期时间单位
     * @return 是否写入成功（键已存在则返回 false）；异常时返回 false
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, json, timeout, unit));
        } catch (Exception e) {
            log.warn("[Redis] setIfAbsent 失败, key={}", key, e);
            return false;
        }
    }
}
