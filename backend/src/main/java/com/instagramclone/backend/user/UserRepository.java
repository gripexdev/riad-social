package com.instagramclone.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByUsernameIn(Collection<String> usernames);
    Page<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String username,
            String fullName,
            Pageable pageable);
}
