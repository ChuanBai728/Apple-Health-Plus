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

@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<ChatSessionResponse> listSessions() {
        return chatService.listSessions();
    }

    @GetMapping("/{sessionId}")
    public ChatSessionResponse getSession(@PathVariable("sessionId") UUID sessionId) {
        return chatService.getSession(sessionId);
    }

    @PostMapping("/{sessionId}/messages")
    public ChatMessageResponse ask(@PathVariable("sessionId") UUID sessionId, @Valid @RequestBody ChatMessageRequest request) {
        return chatService.ask(sessionId, request);
    }
}
