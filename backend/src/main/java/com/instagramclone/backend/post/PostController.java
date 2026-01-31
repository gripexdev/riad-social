package com.instagramclone.backend.post;

import com.instagramclone.backend.storage.FileSystemStorageService;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService postService;
    private final FileSystemStorageService storageService;
    private final CommentReactionService reactionService;

    public PostController(PostService postService, FileSystemStorageService storageService, CommentReactionService reactionService) {
        this.postService = postService;
        this.storageService = storageService;
        this.reactionService = reactionService;
    }

    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(
            @RequestParam("file") MultipartFile file,
            @RequestParam("caption") String caption,
            Principal principal) {
        
        String username = principal.getName();
        String filename = storageService.store(file);
        String imageUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/")
                .path(filename)
                .toUriString();
        
        Post newPost = postService.createPost(imageUrl, caption, username);
        return ResponseEntity.ok(convertToPostResponse(newPost, username));
    }

    @GetMapping("/posts")
    public ResponseEntity<List<PostResponse>> getAllPosts(Principal principal) {
        String currentUsername = principal.getName();
        List<Post> posts = postService.getAllPosts(currentUsername);
        List<PostResponse> postResponses = posts.stream()
                .map(post -> convertToPostResponse(post, currentUsername))
                .collect(Collectors.toList());
        return ResponseEntity.ok(postResponses);
    }

    @GetMapping("/posts/explore")
    public ResponseEntity<List<PostResponse>> getExplorePosts(Principal principal) {
        String currentUsername = principal.getName();
        List<Post> posts = postService.getExplorePosts();
        List<PostResponse> postResponses = posts.stream()
                .map(post -> convertToPostResponse(post, currentUsername))
                .collect(Collectors.toList());
        return ResponseEntity.ok(postResponses);
    }

    @GetMapping("/users/{username}/posts")
    public ResponseEntity<List<PostResponse>> getPostsByUser(@PathVariable String username, Principal principal) {
        String currentUsername = principal.getName();
        List<Post> posts = postService.getPostsByUsername(username);
        List<PostResponse> postResponses = posts.stream()
                .map(post -> convertToPostResponse(post, currentUsername))
                .collect(Collectors.toList());
        return ResponseEntity.ok(postResponses);
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable Long id, Principal principal) {
        String currentUsername = principal.getName();
        return postService.getPostById(id)
                .map(post -> ResponseEntity.ok(convertToPostResponse(post, currentUsername)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<PostResponse> toggleLike(@PathVariable Long id, Principal principal) {
        String likerUsername = principal.getName();
        Post updatedPost = postService.toggleLike(id, likerUsername);
        return ResponseEntity.ok(convertToPostResponse(updatedPost, likerUsername));
    }

    @PostMapping("/posts/{id}/comment")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id, @RequestBody CommentRequest commentRequest, Principal principal) {
        String commenterUsername = principal.getName();
        Comment newComment = postService.addComment(id, commentRequest.getContent(), commenterUsername, commentRequest.getParentCommentId());
        Long parentId = newComment.getParentComment() == null ? null : newComment.getParentComment().getId();
        CommentResponse response = new CommentResponse(
                newComment.getId(),
                newComment.getContent(),
                newComment.getUser().getUsername(),
                newComment.getUser().getProfilePictureUrl(),
                newComment.getCreatedAt(),
                parentId,
                List.of(),
                List.of(),
                null
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/posts/{postId}/comments/{commentId}/reactions")
    public ResponseEntity<CommentReactionSummaryResponse> toggleReplyReaction(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestBody CommentReactionRequest request,
            Principal principal
    ) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return ResponseEntity.status(401).build();
        }
        CommentReactionSummaryResponse response = reactionService.toggleReaction(
                postId,
                commentId,
                request == null ? null : request.getEmoji(),
                principal.getName()
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteReply(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            Principal principal
    ) {
        String username = principal.getName();
        postService.deleteComment(postId, commentId, username);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable Long id,
            @RequestBody UpdatePostRequest updateRequest,
            Principal principal) {
        String username = principal.getName();
        Post updatedPost = postService.updatePostCaption(id, updateRequest.getCaption(), username);
        return ResponseEntity.ok(convertToPostResponse(updatedPost, username));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id, Principal principal) {
        String username = principal.getName();
        postService.deletePost(id, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/uploads/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Resource file = storageService.loadAsResource(filename);
        MediaType mediaType = MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }

    // Initialize storage at application startup
    @PostConstruct
    public void init() {
        storageService.init();
    }
    
    // Helper method to convert Post to PostResponse
    private PostResponse convertToPostResponse(Post post, String currentUsername) {
        boolean likedByCurrentUser = post.getLikedBy().stream()
                .anyMatch(user -> user.getUsername().equals(currentUsername));
        
        // Convert Comments to CommentResponse
        CommentReactionService.CommentReactionLookup reactionLookup =
                reactionService.buildLookup(post.getComments(), currentUsername);
        List<CommentResponse> commentResponses = CommentMapper.toThreadedResponses(post.getComments(), reactionLookup);

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
