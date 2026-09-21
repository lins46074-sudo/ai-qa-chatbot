package com.aidoc.mapper;

import com.aidoc.entity.Document;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户文档 Mapper。
 *
 * <p>继承 MyBatis-Plus BaseMapper，自动获得单表 CRUD / 分页能力；
 * 文档模块与管理模块均通过本接口操作 doc_document 表。</p>
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
