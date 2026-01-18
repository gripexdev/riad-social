package com.instagramclone.backend.notification;

import com.instagramclone.backend.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n join fetch n.actor where n.recipient = :recipient order by n.createdAt desc")
    List<Notification> findByRecipientWithActor(@Param("recipient") User recipient);

    long countByRecipientAndReadIsFalse(User recipient);

    Optional<Notification> findByIdAndRecipient(Long id, User recipient);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipient = :recipient and n.read = false")
    int markAllRead(@Param("recipient") User recipient);
}
