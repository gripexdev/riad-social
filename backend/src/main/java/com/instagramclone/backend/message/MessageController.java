package com.instagramclone.backend.message;

import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationResponse>> getConversations(Principal principal) {
        return ResponseEntity.ok(messageService.getConversations(principal.getName()));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable Long conversationId,
            Principal principal
    ) {
        return ResponseEntity.ok(messageService.getMessages(conversationId, principal.getName()));
    }

    @PutMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markConversationRead(
            @PathVariable Long conversationId,
            Principal principal
    ) {
        messageService.markConversationRead(conversationId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @RequestBody SendMessageRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(messageService.sendMessage(principal.getName(), request));
    }
}
