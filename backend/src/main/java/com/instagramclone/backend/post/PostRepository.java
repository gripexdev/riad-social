package com.instagramclone.backend.post;

import com.instagramclone.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Set;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByUserUsername(String username);
    List<Post> findByUserInOrderByCreatedAtDesc(Set<User> users);
    List<Post> findAllByOrderByCreatedAtDesc();
}
