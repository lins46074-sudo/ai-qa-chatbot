package com.aidoc.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档信息 VO（本人文档列表 / 详情、管理端全库列表共用）。
 *
 * <p>userName 字段：本人列表可为空，管理端列表填充归属用户名。</p>
 */
@Data
public class DocumentVO {

    /** 文档 ID */
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 归属用户名（本人列表可为空，管理列表填充） */
    private String userName;

    /** 原始文件名 */
    private String fileName;

    /** 类型 PDF/TXT */
    private String fileType;

    /** 失败原因（status=2 时非空） */
    private String failReason;

    /** 文件字节数 */
    private Long fileSize;

    /** PDF 页数（TXT 为 0） */
    private Integer pageCount;

    /** 解析文本字符数 */
    private Integer charCount;

    /** 解析状态 0解析中 1就绪 2失败 */
    private Integer status;

    /** 上传时间 */
    private LocalDateTime createTime;
}
