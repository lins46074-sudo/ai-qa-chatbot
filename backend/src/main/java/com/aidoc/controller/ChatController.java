package com.aidoc.controller;

import com.aidoc.common.PageResult;
import com.aidoc.common.Result;
import com.aidoc.dto.MessageSendDTO;
import com.aidoc.dto.SessionCreateDTO;
import com.aidoc.dto.SessionRenameDTO;
import com.aidoc.service.ChatService;
import com.aidoc.service.ChatService.ChatContext;
import com.aidoc.util.UserContext;
import com.aidoc.vo.MessageVO;
import com.aidoc.vo.SessionVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 问答控制器：多会话管理 + 基于文档的流式问答（SSE）。
 *
 * <p>流式问答采用两段式：{@link ChatService#prepare} 先行校验与落库（失败返回 JSON），
 * 成功后才创建 SseEmitter 进入流式阶段 —— 保证参数/权限错误前端拿到的是普通错误体而非半截流。</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 会话分页列表。
     *
     * @param current 页码
     * @param size    每页条数
     * @return 会话分页（含文档名）
     */
    @GetMapping("/session/page")
    public Result<PageResult<SessionVO>> sessionPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(chatService.pageSessions(UserContext.currentUserId(), current, size));
    }

    /**
     * 新建会话。
     *
     * @param dto 新建参数（documentId 必填，title 可空）
     * @return 会话视图
     */
    @PostMapping("/session")
    public Result<SessionVO> createSession(@Valid @RequestBody SessionCreateDTO dto) {
        return Result.ok(chatService.createSession(dto, UserContext.currentUserId()));
    }

    /**
     * 重命名会话。
     *
     * @param id  会话 ID
     * @param dto 新标题
     * @return 会话视图
     */
    @PutMapping("/session/{id}")
    public Result<SessionVO> renameSession(@PathVariable Long id,
                                           @Valid @RequestBody SessionRenameDTO dto) {
        return Result.ok(chatService.renameSession(id, dto.getTitle(), UserContext.currentUserId()));
    }

    /**
     * 删除会话（级联消息与上下文缓存）。
     *
     * @param id 会话 ID
     * @return 成功提示
     */
    @DeleteMapping("/session/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(id, UserContext.currentUserId());
        return Result.ok("会话已删除", null);
    }

    /**
     * 会话历史消息分页（id 倒序，current=1 为最新一页）。
     *
     * @param id      会话 ID
     * @param current 页码
     * @param size    每页条数
     * @return 消息分页
     */
    @GetMapping("/session/{id}/message/page")
    public Result<PageResult<MessageVO>> messagePage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(chatService.pageMessages(id, UserContext.currentUserId(), current, size));
    }

    /**
     * 流式问答（SSE，text/event-stream）。
     *
     * <p>事件协议（data 行 JSON，字段见事件类型）：
     * <ul>
     *   <li>{@code {"event":"session","sessionId":12}} —— 自动新建会话时先行通知；</li>
     *   <li>{@code {"event":"delta","content":"增量"}} —— 模型逐段输出；</li>
     *   <li>{@code {"event":"done","messageId":34,"sessionId":12}} —— 流结束；</li>
     *   <li>{@code {"event":"error","message":"..."}} —— 出错（流终止）。</li>
     * </ul></p>
     *
     * @param dto 提问参数
     * @return SseEmitter（由 Spring MVC 以 text/event-stream 输出）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody MessageSendDTO dto) {
        Long userId = UserContext.currentUserId();
        // 第一阶段：校验 + 落库 + 组装上下文（失败走全局异常处理器返回 JSON）
        ChatContext ctx = chatService.prepare(dto, userId);
        // 第二阶段：创建永不超时的 emitter 并开始流式输出
        SseEmitter emitter = new SseEmitter(0L);
        chatService.streamAnswer(ctx, emitter);
        return emitter;
    }
}
