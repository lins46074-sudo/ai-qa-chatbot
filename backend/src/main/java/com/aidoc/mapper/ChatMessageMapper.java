package com.aidoc.mapper;

import com.aidoc.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问答消息 Mapper。
 *
 * <p>继承 MyBatis-Plus BaseMapper，自动获得单表 CRUD / 分页能力；
 * 会话问答模块通过本接口操作 chat_message 表。</p>
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
