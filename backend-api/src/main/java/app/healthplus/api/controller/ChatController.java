package app.healthplus.api.controller;

import app.healthplus.api.service.ChatService;
import app.healthplus.domain.dto.ChatMessageRequest;
import app.healthplus.domain.dto.ChatMessageResponse;
import app.healthplus.domain.dto.ChatSessionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 健康对话控制器。
 * <p>
 * 职责：管理聊天会话的生命周期，支持用户与 AI 进行健康相关的问答对话。
 * 底层基于 RAG（检索增强生成）技术，结合用户健康数据和知识库提供个性化回答。
 * </p>
 * <p>
 * 对应 API 端点：{@code /api/v1/chat/sessions}
 * </p>
 */
@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatController {

    /** 聊天服务 —— 负理会话管理、消息存储和 AI 对话生成 */
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 获取当前用户的所有聊天会话列表。
     *
     * @return 会话响应列表，按创建时间排序
     */
    @GetMapping
    public List<ChatSessionResponse> listSessions() {
        return chatService.listSessions();
    }

    /**
     * 获取指定会话的详细信息。
     *
     * @param sessionId 聊天会话 ID
     * @return 会话响应，包含会话元信息和最近消息
     */
    @GetMapping("/{sessionId}")
    public ChatSessionResponse getSession(@PathVariable("sessionId") UUID sessionId) {
        return chatService.getSession(sessionId);
    }

    /**
     * 向指定会话发送消息并获取 AI 回复。
     * <p>
     * 该接口会将用户消息持久化到数据库，然后调用 AI 服务
     * （含 RAG 知识库检索）生成回答，最终将 AI 回复也一并存储。
     * </p>
     *
     * @param sessionId 聊天会话 ID
     * @param request   用户消息请求，包含消息文本
     * @return AI 回复响应，包含回答文本和引用的知识来源
     */
    @PostMapping("/{sessionId}/messages")
    public ChatMessageResponse ask(@PathVariable("sessionId") UUID sessionId, @Valid @RequestBody ChatMessageRequest request) {
        return chatService.ask(sessionId, request);
    }
}
