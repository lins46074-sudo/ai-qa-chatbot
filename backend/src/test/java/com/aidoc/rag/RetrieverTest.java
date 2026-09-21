package com.aidoc.rag;

import com.aidoc.ai.LocalEmbeddingModel;
import com.aidoc.entity.DocChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检索器单元测试。
 *
 * <p>用本地哈希向量模型构造切片，验证检索链路的四个关键行为：
 * 能召回相关片段、阈值能过滤无关片段、相邻片段会合并、引用编号连续可溯源。
 * 全程不依赖数据库与外部服务。</p>
 */
class RetrieverTest {

    /** 测试用切片正文（模拟一篇讲系统实现的文档） */
    private static final List<String> CONTENTS = List.of(
            "系统部署环境要求 JDK17 与 MySQL8，Redis 用于缓存登录态与会话上下文。",
            "用户上传文档后，系统会对文本进行切片并逐片向量化，向量以 JSON 形式持久化。",
            "管理端提供数据看板，展示近七日的访问量与问答量趋势，以及访问量最高的文档。",
            "文档解析支持 PDF 与 TXT 两种格式，其中 PDF 通过 PDFBox 抽取文本层。"
    );

    private LocalEmbeddingModel embeddingModel;
    private RagProperties ragProperties;
    private Retriever retriever;
    private List<DocChunk> chunks;

    /**
     * 每个用例前重建切片集合与检索器，避免用例之间互相影响。
     */
    @BeforeEach
    void setUp() {
        embeddingModel = new LocalEmbeddingModel(512);
        ragProperties = new RagProperties();
        ragProperties.setTopK(3);
        ragProperties.setScoreThreshold(0.05D);
        ragProperties.setMergeAdjacent(true);
        ragProperties.setMaxContextChars(4000);
        retriever = new Retriever(embeddingModel, ragProperties);

        // 为每条正文生成向量并组装为切片实体
        List<double[]> vectors = embeddingModel.embedAll(CONTENTS);
        chunks = new ArrayList<>();
        for (int i = 0; i < CONTENTS.size(); i++) {
            DocChunk chunk = new DocChunk();
            chunk.setDocId(1L);
            chunk.setChunkIndex(i);
            chunk.setContent(CONTENTS.get(i));
            chunk.setCharStart(i * 100);
            chunk.setCharEnd(i * 100 + CONTENTS.get(i).length());
            chunk.setEmbedding(VectorMath.toJson(vectors.get(i)));
            chunks.add(chunk);
        }
    }

    @Test
    @DisplayName("应召回与问题最相关的片段，并给出从 1 开始的连续引用编号")
    void shouldRetrieveMostRelevantChunk() {
        List<RetrievedChunk> hits = retriever.retrieve("PDF 文档是怎么解析的？", chunks);

        assertFalse(hits.isEmpty(), "应至少召回一个片段");
        assertTrue(hits.get(0).getContent().contains("PDFBox"),
                "最相关片段应为讲解 PDF 解析的那一条，实际为：" + hits.get(0).getContent());
        // 引用编号必须从 1 开始连续递增，否则前端无法与答案中的 [n] 标注对应
        for (int i = 0; i < hits.size(); i++) {
            assertEquals(i + 1, hits.get(i).getRefIndex(), "引用编号应从 1 连续递增");
        }
    }

    @Test
    @DisplayName("所有片段得分低于阈值时应返回空结果（触发上层拒答）")
    void shouldReturnEmptyWhenBelowThreshold() {
        // 阈值调到 1.0：任何片段都不可能达到，模拟「文档与问题完全无关」
        ragProperties.setScoreThreshold(1.0D);

        List<RetrievedChunk> hits = retriever.retrieve("今天天气怎么样？", chunks);

        assertTrue(hits.isEmpty(), "低于阈值的片段应全部被过滤，返回空列表以触发拒答");
    }

    @Test
    @DisplayName("命中原文档中相邻的切片时应合并为一片，避免上下文碎片")
    void shouldMergeAdjacentChunks() {
        // 第 0、1 片（chunkIndex 连续）都讲系统实现，问题同时命中两者时应收敛为一个片段
        List<DocChunk> pair = List.of(chunks.get(0), chunks.get(1));
        List<RetrievedChunk> hits = retriever.retrieve("系统用什么缓存登录态，文档上传后如何处理？", pair);

        if (!hits.isEmpty()) {
            assertEquals(1, hits.size(), "两个相邻切片应合并为一个片段");
            assertTrue(hits.get(0).getContent().contains("Redis"), "合并结果应包含第一片内容");
        }
    }

    @Test
    @DisplayName("上下文预算不足时应截断，保证注入提示词的内容不超上限")
    void shouldRespectContextBudget() {
        ragProperties.setMaxContextChars(20);

        List<RetrievedChunk> hits = retriever.retrieve("PDF 文档是怎么解析的？", chunks);

        assertFalse(hits.isEmpty(), "预算再小也应保留至少一个片段");
        int total = hits.stream().mapToInt(RetrievedChunk::length).sum();
        assertTrue(total <= 20, "送出片段总长度 " + total + " 应不超过预算 20");
    }

    @Test
    @DisplayName("向量缺失或维度不一致的切片应被跳过，不影响其余切片召回")
    void shouldSkipChunksWithInvalidVector() {
        List<DocChunk> mixed = new ArrayList<>(chunks);
        DocChunk broken = new DocChunk();
        broken.setDocId(1L);
        broken.setChunkIndex(99);
        broken.setContent("这是一条向量损坏的切片");
        broken.setEmbedding("[]");   // 空向量：不可比较
        mixed.add(broken);

        List<RetrievedChunk> hits = retriever.retrieve("PDF 文档是怎么解析的？", mixed);

        assertFalse(hits.isEmpty(), "损坏切片不应影响其余切片的召回");
        assertTrue(hits.stream().noneMatch(h -> h.getContent().contains("向量损坏")),
                "向量不可用的切片不应出现在结果中");
    }

    @Test
    @DisplayName("问题为空或切片为空时应安全返回空列表")
    void shouldHandleEmptyInput() {
        assertTrue(retriever.retrieve("", chunks).isEmpty());
        assertTrue(retriever.retrieve(null, chunks).isEmpty());
        assertTrue(retriever.retrieve("随便问点什么", List.of()).isEmpty());
    }
}
