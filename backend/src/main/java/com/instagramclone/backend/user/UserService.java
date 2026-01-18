package com.instagramclone.backend.user;

import com.instagramclone.backend.notification.NotificationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public UserService(UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }

    public User registerUser(User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalStateException("Username already taken");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalStateException("Email already taken");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // Initialize bio and profilePictureUrl to empty or null if not provided
        if (user.getBio() == null) user.setBio("");
        if (user.getProfilePictureUrl() == null) user.setProfilePictureUrl("");
        return userRepository.save(user);
    }

    public User updatePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User updateProfile(User user, String bio, String profilePictureUrl) {
        if (bio != null) {
            user.setBio(bio);
        }
        if (profilePictureUrl != null) {
            user.setProfilePictureUrl(profilePictureUrl);
        }
        return userRepository.save(user);
    }

    public List<User> searchUsers(String query, int limit) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isEmpty()) {
            return List.of();
        }
        // Cap the limit and keep results ordered for a stable UX.
        int safeLimit = Math.max(1, Math.min(limit, 50));
        PageRequest pageRequest = PageRequest.of(0, safeLimit, Sort.by("username").ascending());
        return userRepository
                .findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(trimmedQuery, trimmedQuery, pageRequest)
                .getContent();
    }

    public void followUser(String followerUsername, String followingUsername) {
        User follower = userRepository.findByUsername(followerUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Follower not found"));
        User following = userRepository.findByUsername(followingUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Following user not found"));

        if (!follower.getFollowing().contains(following)) {
            follower.getFollowing().add(following);
            following.getFollowers().add(follower);
            userRepository.save(follower);
            userRepository.save(following);
            notificationService.createFollowNotification(follower, following);
        }
    }

    public void unfollowUser(String followerUsername, String followingUsername) {
        User follower = userRepository.findByUsername(followerUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Follower not found"));
        User following = userRepository.findByUsername(followingUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Following user not found"));

        if (follower.getFollowing().contains(following)) {
            follower.getFollowing().remove(following);
            following.getFollowers().remove(follower);
            userRepository.save(follower);
            userRepository.save(following);
        }
    }
}
