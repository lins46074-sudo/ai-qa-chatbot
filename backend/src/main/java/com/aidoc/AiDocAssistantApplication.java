package com.aidoc;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Java RAG 私有文档 AI 问答系统 —— 后端启动类。
 *
 * <p>组合注解说明：
 * <ul>
 *   <li>{@link SpringBootApplication}：SpringBoot 启动注解（内含组件扫描 + 自动配置）；</li>
 *   <li>{@link EnableScheduling}：开启定时任务（如文档模块的临时文件清理任务）；</li>
 *   <li>{@link EnableAsync}：开启异步调用（访问日志由 AccessLogRecorder 异步落库）；</li>
 *   <li>{@link MapperScan}：扫描 com.aidoc.mapper 包下全部 MyBatis-Plus Mapper 接口。</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.aidoc.mapper")
public class AiDocAssistantApplication {

    private static final Logger log = LoggerFactory.getLogger(AiDocAssistantApplication.class);

    /**
     * 应用主入口：启动 SpringBoot 容器并打印启动横幅日志。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiDocAssistantApplication.class, args);
        log.info("============================================");
        log.info("  私有文档 AI 问答系统 后端启动成功，接口前缀 /api");
        log.info("  默认端口 8080，接口文档见项目根目录 README.md");
        log.info("============================================");
    }
}
