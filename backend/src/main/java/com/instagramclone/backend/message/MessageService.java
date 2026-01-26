package com.instagramclone.backend.message;

import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MessageService {

    private static final int MESSAGE_PREVIEW_LIMIT = 120;
    private static final int MESSAGE_MAX_LENGTH = 2000;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AttachmentTokenService attachmentTokenService;
    private final String backendBaseUrl;

    public MessageService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            AttachmentTokenService attachmentTokenService,
            @Value("${backend.base-url:http://localhost:8080}") String backendBaseUrl
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.attachmentTokenService = attachmentTokenService;
        this.backendBaseUrl = (backendBaseUrl == null || backendBaseUrl.isBlank())
                ? "http://localhost:8080"
                : backendBaseUrl;
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
                .map(message -> toMessageResponse(message, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageResponse sendMessage(String senderUsername, SendMessageRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message request is required.");
        }
        String recipientUsername = normalize(request.getRecipientUsername());
        if (recipientUsername.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient username is required.");
        }
        if (recipientUsername.equalsIgnoreCase(senderUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot message yourself.");
        }
        String content = normalize(request.getContent());
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
        Message message = createMessageWithAttachments(sender, recipient, content, Collections.emptyList());

        notifyMessageCreated(message);
        return toMessageResponse(message, sender);
    }

    @Transactional
    public Message createMessageWithAttachments(
            User sender,
            User recipient,
            String content,
            List<MessageAttachment> attachments
    ) {
        Conversation conversation = getOrCreateConversation(sender, recipient);
        Message message = new Message(conversation, sender, recipient, content == null ? "" : content.trim());
        if (attachments != null) {
            attachments.forEach(attachment -> attachment.setMessage(message));
            message.setAttachments(attachments);
        }
        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessageAt(savedMessage.getCreatedAt());
        conversation.setLastMessagePreview(buildPreview(savedMessage.getContent(), savedMessage.getAttachments()));
        conversation.setLastMessageSender(sender);
        conversationRepository.save(conversation);
        return savedMessage;
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

    public MessageResponse toMessageResponse(Message message, User viewer) {
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getUsername(),
                message.getRecipient().getUsername(),
                message.getContent(),
                toAttachmentResponses(message, viewer),
                message.getCreatedAt(),
                message.isRead(),
                message.getReadAt()
        );
    }

    public MessageAttachmentResponse toAttachmentResponse(MessageAttachment attachment, User viewer) {
        String url = null;
        String thumbnailUrl = null;
        if (attachment.getStatus() == AttachmentStatus.READY && !isExpired(attachment)) {
            url = buildAttachmentUrl(attachment.getId(), viewer.getId());
            if (attachment.getThumbnailKey() != null) {
                thumbnailUrl = buildThumbnailUrl(attachment.getId(), viewer.getId());
            }
        }
        return new MessageAttachmentResponse(
                attachment.getId(),
                attachment.getType(),
                attachment.getMimeType(),
                attachment.getSizeBytes(),
                attachment.getChecksum(),
                attachment.getWidth(),
                attachment.getHeight(),
                attachment.getDurationSeconds(),
                attachment.getAltText(),
                url,
                thumbnailUrl,
                resolveStatusForResponse(attachment),
                attachment.getExpiresAt(),
                attachment.getOriginalFilename()
        );
    }

    public void notifyMessageCreated(Message message) {
        User sender = message.getSender();
        User recipient = message.getRecipient();
        messagingTemplate.convertAndSendToUser(sender.getUsername(), "/queue/messages", toMessageResponse(message, sender));
        messagingTemplate.convertAndSendToUser(recipient.getUsername(), "/queue/messages", toMessageResponse(message, recipient));
    }

    public void notifyMessageUpdated(Message message) {
        if (message == null) {
            return;
        }
        User sender = message.getSender();
        User recipient = message.getRecipient();
        messagingTemplate.convertAndSendToUser(sender.getUsername(), "/queue/messages", toMessageResponse(message, sender));
        messagingTemplate.convertAndSendToUser(recipient.getUsername(), "/queue/messages", toMessageResponse(message, recipient));
    }

    private List<MessageAttachmentResponse> toAttachmentResponses(Message message, User viewer) {
        List<MessageAttachment> attachments = message.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }
        return attachments.stream()
                .map(attachment -> toAttachmentResponse(attachment, viewer))
                .collect(Collectors.toList());
    }

    private String buildAttachmentUrl(Long attachmentId, Long viewerId) {
        String token = attachmentTokenService.generateToken(attachmentId, viewerId);
        return buildBaseUrl()
                .path("/api/messages/attachments/")
                .path(String.valueOf(attachmentId))
                .queryParam("token", token)
                .toUriString();
    }

    private String buildThumbnailUrl(Long attachmentId, Long viewerId) {
        String token = attachmentTokenService.generateToken(attachmentId, viewerId);
        return buildBaseUrl()
                .path("/api/messages/attachments/")
                .path(String.valueOf(attachmentId))
                .path("/thumbnail")
                .queryParam("token", token)
                .toUriString();
    }

    private UriComponentsBuilder buildBaseUrl() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            return ServletUriComponentsBuilder.fromCurrentContextPath();
        }
        return UriComponentsBuilder.fromHttpUrl(backendBaseUrl);
    }

    private boolean isExpired(MessageAttachment attachment) {
        return attachment.getExpiresAt() != null && attachment.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private AttachmentStatus resolveStatusForResponse(MessageAttachment attachment) {
        if (isExpired(attachment)) {
            return AttachmentStatus.EXPIRED;
        }
        return attachment.getStatus();
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildPreview(String content, List<MessageAttachment> attachments) {
        if (content != null && !content.trim().isEmpty()) {
            String normalized = content.trim().replaceAll("\\s+", " ");
            if (normalized.length() <= MESSAGE_PREVIEW_LIMIT) {
                return normalized;
            }
            return normalized.substring(0, MESSAGE_PREVIEW_LIMIT).trim() + "...";
        }
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        if (attachments.size() == 1) {
            return previewForAttachment(attachments.get(0));
        }
        return attachments.size() + " attachments";
    }

    private String previewForAttachment(MessageAttachment attachment) {
        if (attachment == null || attachment.getType() == null) {
            return "Attachment";
        }
        return switch (attachment.getType()) {
            case IMAGE -> "Photo";
            case VIDEO -> "Video";
            case DOCUMENT -> "Document";
        };
    }
}
