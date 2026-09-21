package com.aidoc.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 热门文档排行 VO（看板 Top10，按解析字符数 char_count 降序）。
 */
@Data
public class TopDocumentVO {

    /** 文档 ID */
    private Long documentId;

    /** 原始文件名 */
    private String fileName;

    /** 归属用户名（联查 sys_user） */
    private String userName;

    /** 文件字节数 */
    private Long fileSize;

    /** 解析文本字符数（排行依据） */
    private Integer charCount;

    /** 上传时间 */
    private LocalDateTime createTime;
}
