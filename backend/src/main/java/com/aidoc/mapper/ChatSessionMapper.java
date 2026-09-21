package com.aidoc.mapper;

import com.aidoc.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问答会话 Mapper。
 *
 * <p>继承 MyBatis-Plus BaseMapper，自动获得单表 CRUD / 分页能力；
 * 会话问答模块通过本接口操作 chat_session 表。</p>
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
