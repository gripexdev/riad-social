package com.instagramclone.backend.profile;

import com.instagramclone.backend.post.CommentMapper;
import com.instagramclone.backend.post.CommentResponse;
import com.instagramclone.backend.post.Post;
import com.instagramclone.backend.post.PostResponse;
import com.instagramclone.backend.post.PostService;
import com.instagramclone.backend.storage.FileSystemStorageService;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserSearchResponse;
import com.instagramclone.backend.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        String currentUsername = principal != null ? principal.getName() : "";
        List<PostResponse> postResponses = posts.stream()
                .map(post -> convertToPostResponse(post, currentUsername))
                .collect(Collectors.toList());

        return ResponseEntity.ok(buildProfileResponse(user, postResponses, currentUser));
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
        List<PostResponse> postResponses = posts.stream()
                .map(post -> convertToPostResponse(post, updatedUser.getUsername()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(buildProfileResponse(updatedUser, postResponses, updatedUser));
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

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            Principal principal) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            return ResponseEntity.ok(List.of());
        }

        final String currentUsername = principal != null ? principal.getName() : null;
        final User currentUser = currentUsername == null
                ? null
                : userService.findByUsername(currentUsername).orElse(null);
        final Set<String> followingUsernames = currentUser == null
                ? Set.of()
                : currentUser.getFollowing().stream()
                    .map(User::getUsername)
                    .collect(Collectors.toSet());

        // Provide a lightweight search response tailored for discovery.
        List<UserSearchResponse> results = userService.searchUsers(normalizedQuery, limit).stream()
                .filter(user -> currentUsername == null || !user.getUsername().equals(currentUsername))
                .map(user -> new UserSearchResponse(
                        user.getUsername(),
                        user.getFullName(),
                        user.getProfilePictureUrl(),
                        followingUsernames.contains(user.getUsername())
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }

    private ProfileResponse buildProfileResponse(User user, List<PostResponse> posts, User currentUser) {
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

    private PostResponse convertToPostResponse(Post post, String currentUsername) {
        boolean likedByCurrentUser = post.getLikedBy().stream()
                .anyMatch(user -> user.getUsername().equals(currentUsername));

        List<CommentResponse> commentResponses = CommentMapper.toThreadedResponses(post.getComments());

        return new PostResponse(
                post.getId(),
                post.getImageUrl(),
                post.getCaption(),
                post.getUser().getUsername(),
                post.getUser().getProfilePictureUrl(),
                post.getCreatedAt(),
                post.getLikedBy().size(),
                likedByCurrentUser,
                commentResponses
        );
    }
}
