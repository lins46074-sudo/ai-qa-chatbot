package com.aidoc.mapper;

import com.aidoc.entity.DocChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档切片 Mapper，对应表 doc_chunk。
 */
@Mapper
public interface DocChunkMapper extends BaseMapper<DocChunk> {

    /**
     * 批量插入切片。
     *
     * <p>一篇文档动辄数百个切片，逐条 insert 会产生同等数量的数据库往返；
     * 这里用一条多值 INSERT 完成，显著缩短文档解析耗时。</p>
     *
     * @param chunks 切片列表（调用方负责分批，避免单条 SQL 过长）
     * @return 受影响行数
     */
    @Insert("<script>"
            + "INSERT INTO doc_chunk (doc_id, chunk_index, content, char_start, char_end, embedding) VALUES "
            + "<foreach collection='chunks' item='item' separator=','>"
            + "(#{item.docId}, #{item.chunkIndex}, #{item.content}, "
            + "#{item.charStart}, #{item.charEnd}, #{item.embedding})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("chunks") List<DocChunk> chunks);
}
