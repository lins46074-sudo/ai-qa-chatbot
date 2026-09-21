package com.aidoc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户文档实体，对应表 doc_document。
 *
 * <p>文件路径一律存相对路径（相对 aidoc.storage.base-dir），运行期用
 * {@code Paths.get(baseDir).resolve(rel)} 拼绝对路径访问。</p>
 */
@Data
@TableName("doc_document")
public class Document {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID（索引 idx_user_id） */
    private Long userId;

    /** 原始文件名 */
    private String fileName;

    /** 原文件相对路径（docs/{userId}/orig/{docId}.{ext}） */
    private String originalPath;

    /** 解析文本相对路径（docs/{userId}/text/{docId}.txt） */
    private String textPath;

    /** 文件字节数 */
    private Long fileSize;

    /** 类型 PDF/TXT */
    private String fileType;

    /** PDF 页数（TXT 为 0） */
    private Integer pageCount;

    /** 解析文本字符数 */
    private Integer charCount;

    /** 解析状态 0解析中 1就绪 2失败 */
    private Integer status;

    /** 失败原因 */
    private String failReason;

    /** 创建时间（DB 默认 CURRENT_TIMESTAMP） */
    private LocalDateTime createTime;

    /** 更新时间（DB 自动 ON UPDATE） */
    private LocalDateTime updateTime;
}
