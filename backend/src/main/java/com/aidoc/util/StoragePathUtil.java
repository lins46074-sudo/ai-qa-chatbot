package com.aidoc.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件存储路径工具（Document 模块）。
 *
 * <p>存储根目录为 aidoc.storage.base-dir（相对 backend 运行目录）下的 ./data 等，内部结构：
 * <pre>
 * ./data/chunks/{md5}/                         分片上传临时目录（{index}.part）
 * ./data/docs/{userId}/orig/{docId}.{ext}      文档原文件
 * ./data/docs/{userId}/text/{docId}.txt        解析文本（UTF-8）
 * </pre>
 * 入库一律保存相对路径（original_path / text_path），读取时经本类还原绝对路径。</p>
 */
@Component
public class StoragePathUtil {

    /** 存储根目录（application.yml: aidoc.storage.base-dir） */
    @Value("${aidoc.storage.base-dir}")
    private String baseDir;

    /**
     * 存储根目录绝对路径（自动创建）。
     *
     * @return 根目录 Path
     */
    public Path baseDir() {
        return ensureDir(Paths.get(baseDir));
    }

    /**
     * 分片临时目录：chunks/{md5}（自动创建）。
     *
     * @param md5 文件整体 MD5（分片任务标识）
     * @return 目录 Path
     */
    public Path chunkDir(String md5) {
        return ensureDir(baseDir().resolve("chunks").resolve(md5));
    }

    /**
     * 分片文件路径：chunks/{md5}/{index}.part。
     *
     * @param md5   分片任务标识
     * @param index 分片序号（0 起）
     * @return 分片文件 Path
     */
    public Path chunkFile(String md5, int index) {
        return chunkDir(md5).resolve(index + ".part");
    }

    /**
     * 合并中间文件路径：chunks/{md5}/merged.tmp。
     *
     * @param md5 分片任务标识
     * @return 中间文件 Path
     */
    public Path mergedTmp(String md5) {
        return chunkDir(md5).resolve("merged.tmp");
    }

    /**
     * 文档原文件绝对路径：docs/{userId}/orig/{docId}.{ext}。
     *
     * @param userId 所属用户
     * @param docId  文档 ID
     * @param ext    扩展名（pdf/txt，不含点）
     * @return 绝对路径 Path
     */
    public Path originalAbs(Long userId, Long docId, String ext) {
        Path dir = ensureDir(baseDir().resolve("docs").resolve(String.valueOf(userId)).resolve("orig"));
        return dir.resolve(docId + "." + ext.toLowerCase());
    }

    /**
     * 解析文本文件绝对路径：docs/{userId}/text/{docId}.txt。
     *
     * @param userId 所属用户
     * @param docId  文档 ID
     * @return 绝对路径 Path
     */
    public Path textAbs(Long userId, Long docId) {
        Path dir = ensureDir(baseDir().resolve("docs").resolve(String.valueOf(userId)).resolve("text"));
        return dir.resolve(docId + ".txt");
    }

    /**
     * 相对路径还原为绝对路径（防目录穿越：拒绝 .. 前缀）。
     *
     * @param relative 数据库保存的相对路径
     * @return 绝对路径 Path
     */
    public Path toAbsolute(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("empty relative path");
        }
        Path rel = Paths.get(relative);
        if (rel.startsWith("..") || rel.isAbsolute()) {
            throw new IllegalArgumentException("非法路径: " + relative);
        }
        return baseDir().resolve(rel);
    }

    /**
     * 确保目录存在并返回。
     *
     * @param dir 目标目录
     * @return 目录 Path
     */
    private Path ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalStateException("创建存储目录失败: " + dir, e);
        }
        return dir;
    }
}
