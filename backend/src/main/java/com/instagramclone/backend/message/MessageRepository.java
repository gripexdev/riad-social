package com.instagramclone.backend.message;

import com.instagramclone.backend.user.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = "attachments")
    List<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    long countByConversationAndRecipientAndReadIsFalse(Conversation conversation, User recipient);

    @Modifying
    @Query("UPDATE Message m SET m.read = true, m.readAt = :readAt " +
            "WHERE m.conversation = :conversation AND m.recipient = :recipient AND m.read = false")
    int markConversationRead(
            @Param("conversation") Conversation conversation,
            @Param("recipient") User recipient,
            @Param("readAt") LocalDateTime readAt
    );
}
