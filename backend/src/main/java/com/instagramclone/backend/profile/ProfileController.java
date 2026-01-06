package com.instagramclone.backend.profile;

import com.instagramclone.backend.post.Post;
import com.instagramclone.backend.post.PostService;
import com.instagramclone.backend.storage.FileSystemStorageService;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final UserService userService;
    private final PostService postService;
    private final FileSystemStorageService storageService;

    public ProfileController(UserService userService, PostService postService, FileSystemStorageService storageService) {
        this.userService = userService;
        this.postService = postService;
        this.storageService = storageService;
    }

    @GetMapping("/{username}")
    public ResponseEntity<ProfileResponse> getUserProfile(@PathVariable String username, Principal principal) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<Post> posts = postService.getPostsByUsername(username);

        User currentUser = null;
        if (principal != null) {
            currentUser = userService.findByUsername(principal.getName()).orElse(null);
        }

        return ResponseEntity.ok(buildProfileResponse(user, posts, currentUser));
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> updateProfile(
            @RequestParam(value = "bio", required = false) String bio,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        User currentUser = userService.findByUsername(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

        String profilePictureUrl = null;
        if (avatar != null && !avatar.isEmpty()) {
            String contentType = avatar.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().build();
            }
            String filename = storageService.store(avatar);
            profilePictureUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/")
                    .path(filename)
                    .toUriString();
        }

        User updatedUser = userService.updateProfile(currentUser, bio, profilePictureUrl);
        List<Post> posts = postService.getPostsByUsername(updatedUser.getUsername());
        return ResponseEntity.ok(buildProfileResponse(updatedUser, posts, updatedUser));
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

    private ProfileResponse buildProfileResponse(User user, List<Post> posts, User currentUser) {
        boolean isFollowing = false;
        if (currentUser != null && !currentUser.getUsername().equals(user.getUsername())) {
            isFollowing = currentUser.getFollowing().contains(user);
        }

        return new ProfileResponse(
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
    }
}
