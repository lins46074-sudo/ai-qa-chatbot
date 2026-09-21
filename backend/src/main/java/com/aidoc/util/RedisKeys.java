package com.aidoc.util;

/**
 * Redis 键常量类（final 工具类，不可实例化）。
 *
 * <p>全项目统一引用本类常量拼接 Redis 键，避免魔法字符串散落各处。各键的格式与用途：</p>
 * <ul>
 *   <li>{@code aidoc:login:{userId}}：登录态缓存，值为 {@code {"token":"...","loginAt":...}}；</li>
 *   <li>{@code aidoc:ctx:{sessionId}}：会话上下文缓存，值为最近 6 条消息 JSON 数组；</li>
 *   <li>{@code aidoc:doc:text:{docId}}：文档全文缓存（全文注入降级模式使用）；</li>
 *   <li>{@code aidoc:doc:chunks:{docId}}：文档切片及其向量缓存（RAG 检索数据源）。</li>
 * </ul>
 *
 * <p>各类缓存的过期时间统一由 application.yml 的 aidoc.redis-ttl.* 配置。</p>
 */
public final class RedisKeys {

    /** 登录态缓存键前缀（完整键 = LOGIN + userId） */
    public static final String LOGIN = "aidoc:login:";

    /** 会话上下文缓存键前缀（完整键 = CHAT_CTX + sessionId） */
    public static final String CHAT_CTX = "aidoc:ctx:";

    /** 文档全文缓存键前缀（完整键 = DOC_TEXT + docId） */
    public static final String DOC_TEXT = "aidoc:doc:text:";

    /** 文档切片向量缓存键前缀（完整键 = DOC_CHUNKS + docId） */
    public static final String DOC_CHUNKS = "aidoc:doc:chunks:";

    /**
     * 私有构造器：常量类禁止实例化。
     */
    private RedisKeys() {
    }
}
