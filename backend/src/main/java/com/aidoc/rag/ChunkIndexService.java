package com.aidoc.rag;

import com.aidoc.ai.EmbeddingModel;
import com.aidoc.entity.DocChunk;
import com.aidoc.mapper.DocChunkMapper;
import com.aidoc.util.RedisKeys;
import com.aidoc.util.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 切片索引服务：负责切片的<b>建库、读取与销毁</b>。
 *
 * <p>把「索引的读写」与「检索算法」分开：
 * {@link Retriever} 只关心如何在给定切片集合中召回片段，
 * 本类只关心切片从哪来、往哪存，二者互不依赖。</p>
 *
 * <p><b>缓存策略</b>：切片向量随文档解析一次性生成，此后在同一篇文档的多轮问答中
 * 会被反复读取，因此与文档全文一样缓存进 Redis（TTL 见 aidoc.redis-ttl.chunk-hours），
 * 未命中时回源数据库并回填。文档删除或重新解析时同步清除缓存，杜绝脏数据。</p>
 */
@Slf4j
@Service
public class ChunkIndexService {

    /** 单条 INSERT 最多包含的切片数（避免 SQL 语句过长超出 max_allowed_packet） */
    private static final int INSERT_BATCH_SIZE = 200;

    /** 切片 Mapper */
    private final DocChunkMapper docChunkMapper;

    /** 文本切片器 */
    private final TextChunker textChunker;

    /** 向量化模型 */
    private final EmbeddingModel embeddingModel;

    /** RAG 配置 */
    private final RagProperties ragProperties;

    /** Redis 工具 */
    private final RedisUtil redisUtil;

    /** 切片向量缓存 TTL（小时，aidoc.redis-ttl.chunk-hours） */
    @Value("${aidoc.redis-ttl.chunk-hours}")
    private long chunkTtlHours;

    /**
     * 构造注入。
     *
     * @param docChunkMapper 切片 Mapper
     * @param textChunker    切片器
     * @param embeddingModel 向量化模型
     * @param ragProperties  RAG 配置
     * @param redisUtil      Redis 工具
     */
    public ChunkIndexService(DocChunkMapper docChunkMapper, TextChunker textChunker,
                             EmbeddingModel embeddingModel, RagProperties ragProperties,
                             RedisUtil redisUtil) {
        this.docChunkMapper = docChunkMapper;
        this.textChunker = textChunker;
        this.embeddingModel = embeddingModel;
        this.ragProperties = ragProperties;
        this.redisUtil = redisUtil;
    }

    /**
     * 为文档建立切片索引：切片 → 向量化 → 批量落库 → 失效旧缓存。
     *
     * <p>重复调用会先清除该文档的旧切片，保证幂等（文档重新解析时不会残留旧向量）。</p>
     *
     * @param docId 文档 ID
     * @param text  文档全文
     * @return 建立的切片数量
     */
    public int index(Long docId, String text) {
        removeIndex(docId);
        List<TextChunk> slices = textChunker.split(text,
                ragProperties.getChunkSize(), ragProperties.getChunkOverlap());
        if (slices.isEmpty()) {
            log.warn("[索引] 文档切片结果为空, docId={}", docId);
            return 0;
        }

        // 1. 批量向量化（一次遍历即可完成，避免逐条调用产生大量请求）
        List<String> contents = slices.stream().map(TextChunk::getContent).toList();
        List<double[]> vectors = embeddingModel.embedAll(contents);
        if (vectors.size() != slices.size()) {
            log.error("[索引] 向量数量({})与切片数量({})不一致, docId={}",
                    vectors.size(), slices.size(), docId);
            return 0;
        }

        // 2. 组装实体
        List<DocChunk> entities = new ArrayList<>(slices.size());
        for (int i = 0; i < slices.size(); i++) {
            TextChunk slice = slices.get(i);
            DocChunk entity = new DocChunk();
            entity.setDocId(docId);
            entity.setChunkIndex(slice.getIndex());
            entity.setContent(slice.getContent());
            entity.setCharStart(slice.getCharStart());
            entity.setCharEnd(slice.getCharEnd());
            entity.setEmbedding(VectorMath.toJson(vectors.get(i)));
            entities.add(entity);
        }

        // 3. 分批插入，减少数据库往返
        int inserted = 0;
        for (int from = 0; from < entities.size(); from += INSERT_BATCH_SIZE) {
            int to = Math.min(from + INSERT_BATCH_SIZE, entities.size());
            inserted += docChunkMapper.insertBatch(entities.subList(from, to));
        }
        log.info("[索引] 文档切片建库完成, docId={}, 切片数={}, 向量模型={}, 维度={}",
                docId, inserted, embeddingModel.name(), embeddingModel.dimension());
        return inserted;
    }

    /**
     * 删除文档的全部切片与缓存。
     *
     * @param docId 文档 ID
     */
    public void removeIndex(Long docId) {
        try {
            docChunkMapper.delete(new LambdaQueryWrapper<DocChunk>().eq(DocChunk::getDocId, docId));
        } catch (Exception e) {
            log.warn("[索引] 删除切片失败, docId={}", docId, e);
        }
        redisUtil.delete(RedisKeys.DOC_CHUNKS + docId);
    }

    /**
     * 读取文档切片：Redis 命中优先，未命中回源数据库并回填缓存。
     *
     * @param docId 文档 ID
     * @return 切片列表（按 chunkIndex 升序）；无切片时返回空列表
     */
    public List<DocChunk> loadChunks(Long docId) {
        List<DocChunk> cached = redisUtil.getList(RedisKeys.DOC_CHUNKS + docId, DocChunk.class);
        if (cached != null) {
            return cached;
        }
        List<DocChunk> chunks = docChunkMapper.selectList(
                new LambdaQueryWrapper<DocChunk>()
                        .eq(DocChunk::getDocId, docId)
                        .orderByAsc(DocChunk::getChunkIndex));
        if (chunks == null) {
            chunks = List.of();
        }
        // 空列表同样缓存，避免对「无切片的文档」反复查库
        redisUtil.set(RedisKeys.DOC_CHUNKS + docId, chunks, chunkTtlHours, TimeUnit.HOURS);
        return chunks;
    }
}
