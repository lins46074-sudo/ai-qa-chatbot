package com.aidoc.service;

import com.aidoc.common.PageResult;
import com.aidoc.dto.ChunkMergeDTO;
import com.aidoc.vo.ChunkStatusVO;
import com.aidoc.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档服务接口：小文件直传、大文件分片上传（断点续传）、解析与删除。
 */
public interface DocumentService {

    /**
     * 小文件直接上传（≤2MB）：保存原文件并同步解析文本。
     *
     * @param userId 当前用户 ID
     * @param file   上传文件（PDF / TXT）
     * @return 文档视图（含解析状态）
     */
    DocumentVO upload(Long userId, MultipartFile file);

    /**
     * 分片上传前置查询：返回已上传的分片序号（支持断点续传 / 秒传判断）。
     *
     * @param userId     当前用户 ID
     * @param identifier 文件 MD5（分片任务标识）
     * @return 已上传分片信息
     */
    ChunkStatusVO chunkStatus(Long userId, String identifier);

    /**
     * 上传单个分片（幂等：已存在则跳过）。
     *
     * @param userId      当前用户 ID
     * @param file        分片文件
     * @param identifier  文件 MD5
     * @param index       分片序号（0 起）
     * @param totalChunks 总分片数
     */
    void uploadChunk(Long userId, MultipartFile file, String identifier, int index, int totalChunks);

    /**
     * 合并分片并解析：完整性校验 → MD5 校验 → 落位原文件 → 解析全文。
     *
     * @param userId 当前用户 ID
     * @param dto    合并参数（identifier / fileName / totalChunks / fileSize）
     * @return 文档视图（含解析状态）
     */
    DocumentVO mergeChunks(Long userId, ChunkMergeDTO dto);

    /**
     * 我的文档分页（支持关键字 / 类型 / 状态组合过滤）。
     *
     * @param userId   当前用户 ID
     * @param current  页码（从 1 起）
     * @param size     每页条数
     * @param keyword  文件名关键字（可空）
     * @param fileType 文档类型 PDF/TXT（可空）
     * @param status   解析状态（可空）
     * @return 分页结果
     */
    PageResult<DocumentVO> pageDocs(Long userId, long current, long size,
                                    String keyword, String fileType, Integer status);

    /**
     * 文档详情（含归属校验）。
     *
     * @param userId 当前用户 ID
     * @param docId  文档 ID
     * @return 文档视图
     */
    DocumentVO getDoc(Long userId, Long docId);

    /**
     * 删除文档：级联删除关联会话 / 消息 / 原文件 / 文本文件 / Redis 缓存。
     *
     * @param userId 当前用户 ID
     * @param docId  文档 ID
     */
    void deleteDoc(Long userId, Long docId);
}
