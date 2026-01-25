package com.instagramclone.backend.message;

import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class MessageRealtimeController {

    private final MessageService messageService;

    public MessageRealtimeController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/messages/typing")
    public void handleTyping(TypingEventRequest request, Principal principal) {
        if (principal == null) {
            return;
        }
        messageService.sendTypingIndicator(principal.getName(), request);
    }
}
