package com.aidoc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切片实体，对应表 doc_chunk —— RAG 检索的最小单元。
 *
 * <p>每篇文档解析完成后被切分为若干切片，逐片向量化后落库；
 * 提问时按余弦相似度在本表内召回 TopK 片段，而非把整篇文档塞进提示词。</p>
 *
 * <p>{@link #embedding} 存的是 JSON 数组字符串（如 {@code [0.12,-0.03,...]}），
 * 这样无需引入向量数据库即可在 MySQL 中持久化向量；<b>检索范围被限定在
 * 单个文档（几百个切片）内，内存中做线性扫描即可达到毫秒级</b>，
 * 因此本量级下没有必要引入 Milvus / pgvector 等专业向量库 ——
 * 若切片规模上升到百万级，只需把本表替换为向量库并保留同一套检索接口。</p>
 */
@Data
@TableName("doc_chunk")
public class DocChunk {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属文档 ID（索引 idx_doc_id） */
    private Long docId;

    /** 切片在文档内的序号（0 起） */
    private Integer chunkIndex;

    /** 切片正文 */
    private String content;

    /** 切片在规范化文本中的起始下标 */
    private Integer charStart;

    /** 切片在规范化文本中的结束下标（不含） */
    private Integer charEnd;

    /** 切片向量（JSON 数组字符串） */
    private String embedding;

    /** 创建时间（DB 默认 CURRENT_TIMESTAMP） */
    private LocalDateTime createTime;
}
