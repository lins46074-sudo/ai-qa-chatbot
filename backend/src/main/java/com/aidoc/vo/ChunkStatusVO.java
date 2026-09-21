package com.aidoc.vo;

import lombok.Data;

import java.util.List;

/**
 * 分片状态 VO（GET /api/doc/chunk/status）。
 *
 * <p>用于秒传与断点续传：前端据此只上传缺失的分片。</p>
 */
@Data
public class ChunkStatusVO {

    /** 分片标识（全文 MD5 小写） */
    private String identifier;

    /** 已上传完成的分片索引列表（从 0 计，升序） */
    private List<Integer> uploaded;
}
