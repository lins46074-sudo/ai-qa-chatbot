package com.aidoc.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话信息 VO（会话分页 / 新建 / 重命名返回）。
 *
 * <p>documentName 由会话模块联查文档表填充（前端会话列表展示当前问答的文档名）。</p>
 */
@Data
public class SessionVO {

    /** 会话 ID */
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 关联文档 ID */
    private Long documentId;

    /** 关联文档名（联查填充） */
    private String documentName;

    /** 会话标题 */
    private String title;

    /** 消息条数 */
    private Integer messageCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间（列表按此倒序展示） */
    private LocalDateTime updateTime;
}
