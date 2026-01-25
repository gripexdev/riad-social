package com.instagramclone.backend.message;

import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MessageService {

    private static final int MESSAGE_PREVIEW_LIMIT = 120;
    private static final int MESSAGE_MAX_LENGTH = 2000;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(String username) {
        User currentUser = loadUser(username);
        return conversationRepository.findByParticipant(currentUser).stream()
                .map(conversation -> toConversationResponse(conversation, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long conversationId, String username) {
        User currentUser = loadUser(username);
        Conversation conversation = getConversationForUser(conversationId, currentUser);
        return messageRepository.findByConversationOrderByCreatedAtAsc(conversation).stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageResponse sendMessage(String senderUsername, SendMessageRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message request is required.");
        }
        String recipientUsername = normalizeUsername(request.getRecipientUsername());
        if (recipientUsername.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient username is required.");
        }
        if (recipientUsername.equalsIgnoreCase(senderUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot message yourself.");
        }
        String content = normalizeContent(request.getContent());
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required.");
        }
        if (content.length() > MESSAGE_MAX_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message content exceeds " + MESSAGE_MAX_LENGTH + " characters."
            );
        }

        User sender = loadUser(senderUsername);
        User recipient = loadUser(recipientUsername);

        Conversation conversation = getOrCreateConversation(sender, recipient);
        Message message = new Message(conversation, sender, recipient, content);
        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessageAt(savedMessage.getCreatedAt());
        conversation.setLastMessagePreview(buildPreview(savedMessage.getContent()));
        conversation.setLastMessageSender(sender);
        conversationRepository.save(conversation);

        MessageResponse response = toMessageResponse(savedMessage);
        messagingTemplate.convertAndSendToUser(recipient.getUsername(), "/queue/messages", response);
        messagingTemplate.convertAndSendToUser(sender.getUsername(), "/queue/messages", response);
        return response;
    }

    @Transactional
    public void markConversationRead(Long conversationId, String username) {
        User currentUser = loadUser(username);
        Conversation conversation = getConversationForUser(conversationId, currentUser);
        messageRepository.markConversationRead(conversation, currentUser, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public void sendTypingIndicator(String senderUsername, TypingEventRequest request) {
        if (request == null || request.getConversationId() == null) {
            return;
        }
        User sender = loadUser(senderUsername);
        Conversation conversation = getConversationForUser(request.getConversationId(), sender);
        User recipient = resolveOtherParticipant(conversation, sender);
        TypingEventResponse response = new TypingEventResponse(
                conversation.getId(),
                sender.getUsername(),
                request.isTyping()
        );
        messagingTemplate.convertAndSendToUser(recipient.getUsername(), "/queue/typing", response);
    }

    private ConversationResponse toConversationResponse(Conversation conversation, User currentUser) {
        User other = resolveOtherParticipant(conversation, currentUser);
        long unreadCount = messageRepository.countByConversationAndRecipientAndReadIsFalse(conversation, currentUser);
        String lastSenderUsername = conversation.getLastMessageSender() == null
                ? null
                : conversation.getLastMessageSender().getUsername();
        return new ConversationResponse(
                conversation.getId(),
                other.getUsername(),
                other.getFullName(),
                other.getProfilePictureUrl(),
                conversation.getLastMessagePreview(),
                conversation.getLastMessageAt(),
                lastSenderUsername,
                unreadCount
        );
    }

    private MessageResponse toMessageResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getUsername(),
                message.getRecipient().getUsername(),
                message.getContent(),
                message.getCreatedAt(),
                message.isRead(),
                message.getReadAt()
        );
    }

    private Conversation getConversationForUser(Long conversationId, User user) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
        if (!isParticipant(conversation, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this conversation.");
        }
        return conversation;
    }

    private boolean isParticipant(Conversation conversation, User user) {
        return Objects.equals(conversation.getUserOne().getId(), user.getId())
                || Objects.equals(conversation.getUserTwo().getId(), user.getId());
    }

    private User resolveOtherParticipant(Conversation conversation, User currentUser) {
        if (Objects.equals(conversation.getUserOne().getId(), currentUser.getId())) {
            return conversation.getUserTwo();
        }
        return conversation.getUserOne();
    }

    private Conversation getOrCreateConversation(User sender, User recipient) {
        User[] orderedUsers = orderUsers(sender, recipient);
        return conversationRepository.findBetweenUsers(orderedUsers[0], orderedUsers[1])
                .orElseGet(() -> conversationRepository.save(new Conversation(orderedUsers[0], orderedUsers[1])));
    }

    private User[] orderUsers(User first, User second) {
        if (first.getId() != null && second.getId() != null) {
            if (first.getId() <= second.getId()) {
                return new User[]{first, second};
            }
            return new User[]{second, first};
        }
        int comparison = first.getUsername().compareToIgnoreCase(second.getUsername());
        if (comparison <= 0) {
            return new User[]{first, second};
        }
        return new User[]{second, first};
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with username: " + username
                ));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }

    private String buildPreview(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= MESSAGE_PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, MESSAGE_PREVIEW_LIMIT).trim() + "...";
    }
}
