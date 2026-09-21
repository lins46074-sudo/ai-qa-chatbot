package com.aidoc.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类。
 *
 * <p>注册分页插件 {@link PaginationInnerInterceptor}：
 * 其原理是拦截分页查询 SQL，自动拼接 LIMIT 语句完成物理分页，
 * 并对 COUNT 查询做优化（能去除 ORDER BY 等冗余部分），
 * 让业务层直接使用 BaseMapper.selectPage 即可获得分页能力。</p>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 拦截器链，并加入 MySQL 方言的分页插件。
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件需指定数据库方言（此处为 MySQL）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
