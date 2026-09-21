package com.aidoc.rag;

import com.aidoc.ai.EmbeddingModel;
import com.aidoc.entity.DocChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 检索器：把用户问题转化为向量，在文档切片中召回最相关的片段。
 *
 * <p>完整流程（每一步都是对召回质量的过滤，而非简单 TopK）：
 * <ol>
 *   <li><b>相似度计算</b>：问题向量与全部切片向量逐一算余弦相似度；</li>
 *   <li><b>阈值过滤</b>：低于 {@code score-threshold} 的片段直接丢弃 ——
 *       这是抑制幻觉的第一道闸门，宁可召回为空也不能把无关内容喂给模型；</li>
 *   <li><b>TopK 截断</b>：按得分降序取前 K 个，控制送入模型的上下文规模；</li>
 *   <li><b>相邻合并</b>：命中的切片若在原文档中相邻，合并为一个片段 ——
 *       避免一个完整语义被切成几片后各自独立、上下文残缺；</li>
 *   <li><b>上下文预算</b>：按文档顺序累加，总字符数不超过 {@code max-context-chars}，
 *       防止超长文档挤爆模型的上下文窗口；</li>
 *   <li><b>引用编号</b>：为最终片段分配 1..N 的编号，供提示词与前端溯源共用。</li>
 * </ol></p>
 *
 * <p>最终结果按<b>文档顺序</b>（chunkIndex 升序）排列而非得分顺序，
 * 使注入提示词的参考资料与原文顺序一致，模型更易理解上下文连贯性。</p>
 */
@Slf4j
@Component
public class Retriever {

    /** 向量化模型 */
    private final EmbeddingModel embeddingModel;

    /** RAG 配置 */
    private final RagProperties ragProperties;

    /**
     * 构造注入。
     *
     * @param embeddingModel 向量化模型
     * @param ragProperties  RAG 配置
     */
    public Retriever(EmbeddingModel embeddingModel, RagProperties ragProperties) {
        this.embeddingModel = embeddingModel;
        this.ragProperties = ragProperties;
    }

    /**
     * 执行检索。
     *
     * @param question 用户问题
     * @param chunks   待检索的文档切片（须已含向量）
     * @return 召回的片段列表（已过滤、合并、编号）；无命中时返回空列表
     */
    public List<RetrievedChunk> retrieve(String question, List<DocChunk> chunks) {
        if (question == null || question.isBlank() || chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        // 1. 问题向量化
        double[] questionVector = embeddingModel.embed(question);
        if (questionVector.length == 0) {
            log.warn("[检索] 问题向量化结果为空，跳过检索");
            return List.of();
        }

        // 2. 逐片算分 + 阈值过滤
        List<Scored> scored = new ArrayList<>();
        int dimensionMismatch = 0;
        for (DocChunk chunk : chunks) {
            double[] vector = VectorMath.fromJson(chunk.getEmbedding());
            if (vector.length != questionVector.length) {
                // 维度不一致：通常发生在切换了 Embedding 模型但切片向量未重建，
                // 此时该切片不可比较，跳过即可（重新解析文档会重建全部向量）
                dimensionMismatch++;
                continue;
            }
            double score = VectorMath.cosine(questionVector, vector);
            if (score < ragProperties.getScoreThreshold()) {
                continue;
            }
            scored.add(new Scored(chunk, score));
        }
        if (dimensionMismatch > 0) {
            log.warn("[检索] 有 {} 个切片向量维度与当前模型不一致，已跳过；请重新解析该文档以重建向量",
                    dimensionMismatch);
        }
        if (scored.isEmpty()) {
            log.info("[检索] 未命中任何片段（问题与文档相似度均低于阈值 {}）",
                    ragProperties.getScoreThreshold());
            return List.of();
        }

        // 3. 按得分降序取 TopK
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<Scored> topK = new ArrayList<>(
                scored.subList(0, Math.min(ragProperties.getTopK(), scored.size())));

        // 4. 相邻合并
        List<RetrievedChunk> candidates = ragProperties.isMergeAdjacent()
                ? mergeAdjacent(topK)
                : toChunks(topK);

        // 5. 上下文预算裁剪
        List<RetrievedChunk> budgeted = applyBudget(candidates);

        // 6. 按文档顺序排列并编号
        budgeted.sort(Comparator.comparingInt(RetrievedChunk::getChunkIndex));
        for (int i = 0; i < budgeted.size(); i++) {
            budgeted.get(i).setRefIndex(i + 1);
        }
        log.info("[检索] 命中 {} 片，合并裁剪后送出 {} 片，最高分={}",
                scored.size(), budgeted.size(),
                String.format("%.4f", topK.get(0).score()));
        return budgeted;
    }

    /**
     * 合并原文档中相邻的命中切片。
     *
     * <p>切片序号连续（差值为 1）即视为相邻，合并后取组内最高分作为该片段得分，
     * 使得「一段完整内容被切成三片、三片都被召回」时只占用一个引用编号，
     * 既节省上下文又保证语义完整。</p>
     *
     * @param topK 已按得分降序排列的命中结果
     * @return 合并后的片段列表
     */
    private List<RetrievedChunk> mergeAdjacent(List<Scored> topK) {
        List<Scored> sorted = new ArrayList<>(topK);
        sorted.sort(Comparator.comparingInt(s -> s.chunk().getChunkIndex()));

        List<RetrievedChunk> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        Scored groupStart = null;
        Scored previous = null;

        for (Scored current : sorted) {
            boolean adjacent = previous != null
                    && current.chunk().getChunkIndex() == previous.chunk().getChunkIndex() + 1;
            if (previous != null && !adjacent) {
                merged.add(build(groupStart, previous, buffer));
                buffer.setLength(0);
                groupStart = null;
            }
            if (buffer.length() > 0) {
                buffer.append("\n");
            }
            buffer.append(safeContent(current.chunk()));
            if (groupStart == null) {
                groupStart = current;
            }
            // 组内保留最高分
            if (current.score() > groupStart.score()) {
                groupStart = new Scored(groupStart.chunk(), current.score());
            }
            previous = current;
        }
        if (previous != null && buffer.length() > 0) {
            merged.add(build(groupStart, previous, buffer));
        }
        return merged;
    }

    /**
     * 按组首尾切片构造合并结果。
     *
     * @param head   组内得分最高的记录（代表该组得分）
     * @param tail   组内最后一个切片
     * @param buffer 已拼接的正文
     * @return 合并后的片段
     */
    private RetrievedChunk build(Scored head, Scored tail, StringBuilder buffer) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setChunkIndex(head.chunk().getChunkIndex());
        chunk.setContent(buffer.toString());
        chunk.setScore(head.score());
        chunk.setCharStart(head.chunk().getCharStart() == null ? 0 : head.chunk().getCharStart());
        chunk.setCharEnd(tail.chunk().getCharEnd() == null ? 0 : tail.chunk().getCharEnd());
        return chunk;
    }

    /**
     * 不合并时直接把命中记录转为片段。
     *
     * @param topK 命中记录
     * @return 片段列表
     */
    private List<RetrievedChunk> toChunks(List<Scored> topK) {
        List<RetrievedChunk> result = new ArrayList<>(topK.size());
        for (Scored scored : topK) {
            DocChunk source = scored.chunk();
            RetrievedChunk chunk = new RetrievedChunk();
            chunk.setChunkIndex(source.getChunkIndex() == null ? 0 : source.getChunkIndex());
            chunk.setContent(safeContent(source));
            chunk.setScore(scored.score());
            chunk.setCharStart(source.getCharStart() == null ? 0 : source.getCharStart());
            chunk.setCharEnd(source.getCharEnd() == null ? 0 : source.getCharEnd());
            result.add(chunk);
        }
        return result;
    }

    /**
     * 上下文预算裁剪：按文档顺序累加，超出预算即停止。
     *
     * <p>若首个片段本身就超预算，则截断其正文后保留 —— 保证至少有参考资料可用，
     * 而不是因为一篇超长片段导致整次检索为空。</p>
     *
     * @param candidates 候选片段
     * @return 裁剪后的片段列表
     */
    private List<RetrievedChunk> applyBudget(List<RetrievedChunk> candidates) {
        int budget = ragProperties.getMaxContextChars();
        if (budget <= 0) {
            return candidates;
        }
        List<RetrievedChunk> result = new ArrayList<>();
        int used = 0;
        for (RetrievedChunk chunk : candidates) {
            int length = chunk.length();
            if (used + length <= budget) {
                result.add(chunk);
                used += length;
                continue;
            }
            if (result.isEmpty()) {
                String content = chunk.getContent();
                chunk.setContent(content.substring(0, Math.min(budget, content.length())));
                result.add(chunk);
            }
            // 预算已满，后续片段一律丢弃
            break;
        }
        return result;
    }

    /**
     * 取切片正文（防空指针）。
     *
     * @param chunk 切片
     * @return 正文
     */
    private String safeContent(DocChunk chunk) {
        return chunk.getContent() == null ? "" : chunk.getContent();
    }

    /**
     * 切片 + 得分的中间记录。
     *
     * @param chunk 切片
     * @param score 相似度得分
     */
    private record Scored(DocChunk chunk, double score) {
    }
}
