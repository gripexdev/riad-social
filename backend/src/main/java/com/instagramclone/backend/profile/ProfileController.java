package com.instagramclone.backend.profile;

import com.instagramclone.backend.post.Post;
import com.instagramclone.backend.post.PostService;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final UserService userService;
    private final PostService postService;

    public ProfileController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    @GetMapping("/{username}")
    public ResponseEntity<ProfileResponse> getUserProfile(@PathVariable String username, Principal principal) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<Post> posts = postService.getPostsByUsername(username);

        boolean isFollowing = false;
        if (principal != null) {
            User currentUser = userService.findByUsername(principal.getName()).orElse(null);
            if (currentUser != null) {
                isFollowing = currentUser.getFollowing().contains(user);
            }
        }
        
        ProfileResponse profileResponse = new ProfileResponse(
                user.getUsername(),
                user.getFullName(),
                user.getBio(),
                user.getProfilePictureUrl(),
                posts.size(),
                user.getFollowers().size(),
                user.getFollowing().size(),
                posts,
                isFollowing
        );
        return ResponseEntity.ok(profileResponse);
    }

    @PostMapping("/{username}/follow")
    public ResponseEntity<Void> followUser(@PathVariable String username, Principal principal) {
        String currentUsername = principal.getName();
        userService.followUser(currentUsername, username);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{username}/unfollow")
    public ResponseEntity<Void> unfollowUser(@PathVariable String username, Principal principal) {
        String currentUsername = principal.getName();
        userService.unfollowUser(currentUsername, username);
        return ResponseEntity.ok().build();
    }
}
