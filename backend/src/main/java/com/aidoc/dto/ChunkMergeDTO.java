package com.aidoc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分片合并请求 DTO（POST /api/doc/chunk/merge）。
 *
 * <p>identifier = 全文 MD5（小写），服务端合并后重算 MD5 与之比对校验完整性。</p>
 */
@Data
public class ChunkMergeDTO {

    /** 分片标识（全文 MD5 小写，最长 64） */
    @NotBlank
    @Size(max = 64)
    private String identifier;

    /** 原始文件名（最长 255） */
    @NotBlank
    @Size(max = 255)
    private String fileName;

    /** 分片总数（1 ~ 10000） */
    @NotNull
    @Min(1)
    @Max(10000)
    private Integer totalChunks;

    /** 文件总字节数（≥ 1） */
    @NotNull
    @Min(1)
    private Long fileSize;
}
