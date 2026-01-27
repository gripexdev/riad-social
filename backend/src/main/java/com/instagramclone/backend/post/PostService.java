package com.instagramclone.backend.post;

import com.instagramclone.backend.notification.NotificationService;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserService; // Import UserService
import com.instagramclone.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository; // Keep UserRepository for other user lookups
    private final UserService userService; // Inject UserService
    private final CommentRepository commentRepository;
    private final NotificationService notificationService;

    public PostService(PostRepository postRepository, UserRepository userRepository, UserService userService, CommentRepository commentRepository, NotificationService notificationService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.commentRepository = commentRepository;
        this.notificationService = notificationService;
    }

    public Post createPost(String imageUrl, String caption, String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            throw new IllegalArgumentException("User not found with username: " + username);
        }
        User user = userOptional.get();
        Post post = new Post(imageUrl, caption, user);
        post.setLikes(0);
        return postRepository.save(post);
    }

    public List<Post> getAllPosts(String currentUsername) {
        User currentUser = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("Current user not found"));

        Set<User> followedUsers = currentUser.getFollowing();
        followedUsers.add(currentUser); // Include current user's own posts

        // Fetch posts from current user and followed users
        return postRepository.findByUserInOrderByCreatedAtDesc(followedUsers);
    }

    public List<Post> getExplorePosts() {
        return postRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<Post> getPostById(Long id) {
        return postRepository.findById(id);
    }

    public List<Post> getPostsByUsername(String username) {
        return postRepository.findByUserUsername(username);
    }

    @Transactional
    public Post toggleLike(Long postId, String likerUsername) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found with id: " + postId));
        User liker = userRepository.findByUsername(likerUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + likerUsername));

        boolean alreadyLiked = post.getLikedBy().contains(liker);
        if (alreadyLiked) {
            post.getLikedBy().remove(liker);
        } else {
            post.getLikedBy().add(liker);
            notificationService.createLikeNotification(liker, post);
        }
        post.setLikes(post.getLikedBy().size());
        return postRepository.save(post);
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found with id: " + postId));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found with id: " + commentId));
        if (comment.getPost() == null || !comment.getPost().getId().equals(post.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment does not belong to this post.");
        }
        if (comment.getUser() == null || !comment.getUser().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own reply.");
        }
        if (comment.getParentComment() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only replies can be deleted.");
        }
        commentRepository.delete(comment);
    }

    public Comment addComment(Long postId, String content, String commenterUsername) {
        return addComment(postId, content, commenterUsername, null);
    }

    public Comment addComment(Long postId, String content, String commenterUsername, Long parentCommentId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found with id: " + postId));
        User commenter = userRepository.findByUsername(commenterUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + commenterUsername));

        Comment parentComment = null;
        if (parentCommentId != null) {
            parentComment = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new IllegalArgumentException("Comment not found with id: " + parentCommentId));
            if (parentComment.getPost() == null || !parentComment.getPost().getId().equals(postId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply must target a comment on the same post.");
            }
            if (parentComment.getParentComment() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replies can only target top-level comments.");
            }
        }

        Comment comment = new Comment(content, commenter, post, parentComment);
        Comment savedComment = commentRepository.save(comment);
        if (parentComment != null && parentComment.getUser() != null) {
            notificationService.createCommentNotification(commenter, post, savedComment, parentComment.getUser());
            if (post.getUser() != null
                    && !post.getUser().getUsername().equals(commenter.getUsername())
                    && !post.getUser().getUsername().equals(parentComment.getUser().getUsername())) {
                notificationService.createCommentNotification(commenter, post, savedComment);
            }
        } else {
            notificationService.createCommentNotification(commenter, post, savedComment);
        }
        return savedComment;
    }

    @Transactional
    public Post updatePostCaption(Long postId, String caption, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found with id: " + postId));
        if (!post.getUser().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own posts.");
        }
        if (caption == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caption is required.");
        }
        post.setCaption(caption);
        return postRepository.save(post);
    }

    @Transactional
    public void deletePost(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found with id: " + postId));
        if (!post.getUser().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own posts.");
        }
        postRepository.delete(post);
    }
}
