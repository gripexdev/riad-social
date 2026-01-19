package com.instagramclone.backend.user;

import com.instagramclone.backend.notification.NotificationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private NotificationService notificationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, notificationService);
    }

    @Test
    void searchUsers_returnsEmptyWhenQueryBlank() {
        List<User> results = userService.searchUsers("   ", 10);

        assertTrue(results.isEmpty());
        verify(userRepository, never())
                .findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(any(), any(), any());
    }

    @Test
    void searchUsers_capsLimitAndReturnsResults() {
        User user = new User();
        user.setUsername("alice");
        when(userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(eq("al"), eq("al"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));

        List<User> results = userService.searchUsers("al", 100);

        assertEquals(1, results.size());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(eq("al"), eq("al"), captor.capture());
        assertEquals(50, captor.getValue().getPageSize());
    }

    @Test
    void followUser_savesAndNotifies() {
        User follower = buildUser("follower");
        User following = buildUser("following");
        when(userRepository.findByUsername("follower")).thenReturn(Optional.of(follower));
        when(userRepository.findByUsername("following")).thenReturn(Optional.of(following));

        userService.followUser("follower", "following");

        assertTrue(follower.getFollowing().contains(following));
        assertTrue(following.getFollowers().contains(follower));
        verify(userRepository).save(follower);
        verify(userRepository).save(following);
        verify(notificationService).createFollowNotification(follower, following);
    }

    @Test
    void unfollowUser_removesRelationship() {
        User follower = buildUser("follower");
        User following = buildUser("following");
        follower.getFollowing().add(following);
        following.getFollowers().add(follower);

        when(userRepository.findByUsername("follower")).thenReturn(Optional.of(follower));
        when(userRepository.findByUsername("following")).thenReturn(Optional.of(following));

        userService.unfollowUser("follower", "following");

        assertTrue(follower.getFollowing().isEmpty());
        assertTrue(following.getFollowers().isEmpty());
        verify(userRepository).save(follower);
        verify(userRepository).save(following);
    }

    private User buildUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("password");
        return user;
    }
}
