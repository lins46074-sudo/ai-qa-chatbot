package com.aidoc.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问答消息 VO（历史消息分页返回）。
 */
@Data
public class MessageVO {

    /** 消息 ID */
    private Long id;

    /** 所属会话 ID */
    private Long sessionId;

    /** 角色 USER/ASSISTANT */
    private String role;

    /** 消息内容 */
    private String content;

    /** 引用来源（仅 ASSISTANT 消息可能非空，用于历史消息回显答案依据） */
    private List<SourceVO> sources;

    /** 发送时间 */
    private LocalDateTime createTime;
}
