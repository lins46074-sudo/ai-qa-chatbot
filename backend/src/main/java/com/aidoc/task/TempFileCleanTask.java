package com.aidoc.task;

import com.aidoc.util.StoragePathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 临时文件定时清理任务。
 *
 * <p>分片上传的临时分片（chunks/{md5}）在合并成功后会立即删除；但若用户中途放弃上传，
 * 会残留"孤儿分片目录"。本任务负责兜底清理：
 * <ul>
 *   <li>每日凌晨 2 点执行一次（业务低峰）；</li>
 *   <li>另设 6 小时间隔的兜底调度，防止错过执行窗口。</li>
 * </ul>
 * 判定规则：分片目录最后修改时间超过 24 小时即视为孤儿并整体递归删除。</p>
 */
@Slf4j
@Component
public class TempFileCleanTask {

    /** 孤儿分片目录判定阈值（24 小时） */
    private static final Duration EXPIRE_AFTER = Duration.ofHours(24);

    private final StoragePathUtil storagePathUtil;

    public TempFileCleanTask(StoragePathUtil storagePathUtil) {
        this.storagePathUtil = storagePathUtil;
    }

    /**
     * 主调度：每日 02:00 执行。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanDaily() {
        cleanExpiredChunks();
    }

    /**
     * 兜底调度：每 6 小时执行一次（初始延迟 6 小时，避开刚启动即清理的场景）。
     */
    @Scheduled(fixedDelay = 6 * 3600_000L, initialDelay = 6 * 3600_000L)
    public void cleanFallback() {
        cleanExpiredChunks();
    }

    /**
     * 遍历 chunks 目录，删除修改时间超过 24 小时的子目录。
     */
    private void cleanExpiredChunks() {
        Path chunksRoot = storagePathUtil.baseDir().resolve("chunks");
        if (!Files.isDirectory(chunksRoot)) {
            return;
        }
        AtomicInteger removed = new AtomicInteger(0);
        Instant now = Instant.now();
        try (Stream<Path> dirs = Files.list(chunksRoot)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    FileTime mtime = Files.getLastModifiedTime(dir);
                    if (Duration.between(mtime.toInstant(), now).compareTo(EXPIRE_AFTER) > 0) {
                        // 目录整体递归删除（先删子项再删目录）
                        try (Stream<Path> walk = Files.walk(dir)) {
                            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (Exception ignored) {
                                    // 单文件删除失败不中断整体清理
                                }
                            });
                        }
                        removed.incrementAndGet();
                        log.info("[临时清理] 已清理过期分片目录: {}", dir.getFileName());
                    }
                } catch (Exception e) {
                    log.warn("[临时清理] 处理目录失败: {}", dir, e);
                }
            });
        } catch (Exception e) {
            log.warn("[临时清理] 扫描 chunks 目录失败", e);
        }
        if (removed.get() > 0) {
            log.info("[临时清理] 本次共清理 {} 个过期分片目录", removed.get());
        }
    }
}
