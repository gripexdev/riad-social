import { Component, Input, OnInit } from '@angular/core';
import { Post, PostService, CommentResponse } from '../post.service';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

@Component({
  selector: 'app-post-card',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    ReactiveFormsModule
  ],
  templateUrl: './post-card.component.html',
  styleUrl: './post-card.component.scss'
})
export class PostCardComponent implements OnInit {
  @Input() post!: Post;
  commentForm!: FormGroup;
  showComments: boolean = false;
  currentUsername: string | null = null;

  constructor(
    private postService: PostService,
    private authService: AuthService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.currentUsername = this.authService.getUsername();
    this.commentForm = this.fb.group({
      comment: ['', Validators.required]
    });
  }

  toggleLike(): void {
    if (!this.currentUsername) {
      // Handle not logged in case (e.g., redirect to login)
      return;
    }
    this.postService.toggleLike(this.post.id).subscribe({
      next: (updatedPost) => {
        this.post.likesCount = updatedPost.likesCount;
        this.post.likedByCurrentUser = updatedPost.likedByCurrentUser;
      },
      error: (err) => console.error('Error toggling like', err)
    });
  }

  addComment(): void {
    if (this.commentForm.valid && this.currentUsername) {
      const content = this.commentForm.value.comment;
      this.postService.addComment(this.post.id, content).subscribe({
        next: (newCommentBackend) => {
          // Manually create a CommentResponse that matches the backend's DTO
          const newComment: CommentResponse = {
            id: newCommentBackend.id,
            content: newCommentBackend.content,
            username: this.currentUsername!,
            createdAt: new Date().toISOString()
          };
          this.post.comments.push(newComment);
          this.commentForm.reset();
        },
        error: (err) => console.error('Error adding comment', err)
      });
    }
  }

  toggleComments(): void {
    this.showComments = !this.showComments;
  }
}
