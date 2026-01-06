package com.instagramclone.backend.post;

import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserService; // Import UserService
import com.instagramclone.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository; // Keep UserRepository for other user lookups
    private final UserService userService; // Inject UserService
    private final CommentRepository commentRepository;

    public PostService(PostRepository postRepository, UserRepository userRepository, UserService userService, CommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.commentRepository = commentRepository;
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

        if (post.getLikedBy().contains(liker)) {
            post.getLikedBy().remove(liker);
        } else {
            post.getLikedBy().add(liker);
        }
        post.setLikes(post.getLikedBy().size());
        return postRepository.save(post);
    }

    public Comment addComment(Long postId, String content, String commenterUsername) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found with id: " + postId));
        User commenter = userRepository.findByUsername(commenterUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + commenterUsername));

        Comment comment = new Comment(content, commenter, post);
        return commentRepository.save(comment);
    }
}
