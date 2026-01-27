import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Post, PostService, CommentResponse } from '../post.service';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { PostDialogService } from '../post-dialog.service';

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
export class PostCardComponent implements OnInit, OnDestroy {
  @Input() post!: Post;
  @Input() canEdit: boolean = false;
  @Input() canDelete: boolean = false;
  @Output() postDeleted = new EventEmitter<number>();
  commentForm!: FormGroup;
  replyForm!: FormGroup;
  editForm!: FormGroup;
  showComments: boolean = false;
  currentUsername: string | null = null;
  showActions: boolean = false;
  isEditing: boolean = false;
  isSaving: boolean = false;
  errorMessage: string | null = null;
  showDeleteConfirm: boolean = false;
  activeReplyCommentId: number | null = null;
  replyTargetUsername: string | null = null;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private postService: PostService,
    private authService: AuthService,
    private fb: FormBuilder,
    private postDialogService: PostDialogService
  ) {}

  ngOnInit(): void {
    this.currentUsername = this.authService.getUsername();
    this.commentForm = this.fb.group({
      comment: ['', Validators.required]
    });
    this.replyForm = this.fb.group({
      reply: ['', Validators.required]
    });
    this.editForm = this.fb.group({
      caption: ['']
    });
    this.postDialogService.activeDeletePostId$
      .pipe(takeUntil(this.destroy$))
      .subscribe((activeId) => {
        this.showDeleteConfirm = activeId === this.post.id;
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
            username: newCommentBackend.username || this.currentUsername!,
            createdAt: newCommentBackend.createdAt || new Date().toISOString(),
            parentId: newCommentBackend.parentId ?? null,
            replies: newCommentBackend.replies ?? []
          };
          this.post.comments.push(newComment);
          this.commentForm.reset();
        },
        error: (err) => console.error('Error adding comment', err)
      });
    }
  }

  startReply(comment: CommentResponse): void {
    if (!this.currentUsername) {
      return;
    }
    this.activeReplyCommentId = comment.id;
    this.replyTargetUsername = comment.username;
    this.replyForm.reset();
  }

  cancelReply(): void {
    this.activeReplyCommentId = null;
    this.replyTargetUsername = null;
    this.replyForm.reset();
  }

  addReply(comment: CommentResponse): void {
    if (!this.currentUsername || !this.replyForm.valid) {
      return;
    }
    const content = this.replyForm.value.reply;
    this.postService.addComment(this.post.id, content, comment.id).subscribe({
      next: (replyBackend) => {
        const reply: CommentResponse = {
          id: replyBackend.id,
          content: replyBackend.content,
          username: replyBackend.username || this.currentUsername!,
          createdAt: replyBackend.createdAt || new Date().toISOString(),
          parentId: replyBackend.parentId ?? comment.id,
          replies: []
        };
        if (!comment.replies) {
          comment.replies = [];
        }
        comment.replies.push(reply);
        this.cancelReply();
      },
      error: (err) => console.error('Error adding reply', err)
    });
  }

  deleteReply(parentComment: CommentResponse, reply: CommentResponse): void {
    if (!this.currentUsername || reply.username !== this.currentUsername) {
      return;
    }
    this.postService.deleteComment(this.post.id, reply.id).subscribe({
      next: () => {
        if (!parentComment.replies) {
          return;
        }
        parentComment.replies = parentComment.replies.filter((item) => item.id !== reply.id);
      },
      error: (err) => console.error('Error deleting reply', err)
    });
  }

  toggleComments(): void {
    this.showComments = !this.showComments;
  }

  get isOwner(): boolean {
    return !!this.currentUsername && this.currentUsername === this.post.username;
  }

  get hasActions(): boolean {
    return this.isOwner && (this.canEdit || this.canDelete);
  }

  toggleActions(): void {
    if (!this.hasActions) {
      return;
    }
    this.showActions = !this.showActions;
  }

  startEdit(): void {
    if (!this.isOwner || !this.canEdit) {
      return;
    }
    this.errorMessage = null;
    this.isEditing = true;
    this.showActions = false;
    this.editForm.patchValue({ caption: this.post.caption || '' });
  }

  cancelEdit(): void {
    this.isEditing = false;
    this.errorMessage = null;
    this.editForm.patchValue({ caption: this.post.caption || '' });
  }

  saveEdit(): void {
    if (!this.isOwner || !this.canEdit || this.isSaving) {
      return;
    }
    const caption = (this.editForm.value.caption || '').trim();
    this.isSaving = true;
    this.errorMessage = null;
    this.postService.updatePost(this.post.id, caption).subscribe({
      next: (updatedPost) => {
        this.post.caption = updatedPost.caption;
        this.isSaving = false;
        this.isEditing = false;
      },
      error: (err) => {
        console.error('Error updating post', err);
        this.isSaving = false;
        this.errorMessage = 'Failed to update post. Please try again.';
      }
    });
  }

  openDeleteConfirm(): void {
    if (!this.isOwner || !this.canDelete) {
      return;
    }
    this.errorMessage = null;
    this.showActions = false;
    this.postDialogService.openDelete(this.post.id);
  }

  closeDeleteConfirm(): void {
    if (this.isSaving) {
      return;
    }
    this.postDialogService.closeDelete();
  }

  confirmDelete(): void {
    if (!this.isOwner || !this.canDelete || this.isSaving) {
      return;
    }
    this.showActions = false;
    this.isSaving = true;
    this.errorMessage = null;
    this.postService.deletePost(this.post.id).subscribe({
      next: () => {
        this.isSaving = false;
        this.postDialogService.closeDelete();
        this.postDeleted.emit(this.post.id);
      },
      error: (err) => {
        console.error('Error deleting post', err);
        this.isSaving = false;
        this.errorMessage = 'Failed to delete post. Please try again.';
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
