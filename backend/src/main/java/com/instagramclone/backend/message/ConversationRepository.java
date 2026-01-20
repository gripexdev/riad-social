package com.instagramclone.backend.message;

import com.instagramclone.backend.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c " +
            "WHERE c.userOne = :user OR c.userTwo = :user " +
            "ORDER BY c.lastMessageAt DESC")
    List<Conversation> findByParticipant(@Param("user") User user);

    @Query("SELECT c FROM Conversation c " +
            "WHERE (c.userOne = :userA AND c.userTwo = :userB) " +
            "OR (c.userOne = :userB AND c.userTwo = :userA)")
    Optional<Conversation> findBetweenUsers(@Param("userA") User userA, @Param("userB") User userB);
}
