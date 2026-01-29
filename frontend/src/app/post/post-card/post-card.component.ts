import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { Post, PostService, CommentResponse } from '../post.service';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs/operators';
import { PostDialogService } from '../post-dialog.service';
import { ProfileService, UserSearchResult } from '../../profile/profile.service';

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
export class PostCardComponent implements OnInit, OnChanges, OnDestroy {
  @Input() post!: Post;
  @Input() canEdit: boolean = false;
  @Input() canDelete: boolean = false;
  @Input() autoOpenComments: boolean = false;
  @Input() focusCommentId: number | null = null;
  @Input() focusReplyId: number | null = null;
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
  readonly reactionEmojis = ['❤️', '😂', '😮', '😢', '😡', '👍'];
  private reactingReplyIds = new Set<number>();
  activeReactionPickerId: number | null = null;
  mentionResults: UserSearchResult[] = [];
  mentionOpen = false;
  mentionIndex = 0;
  mentionActiveInput: 'comment' | 'reply' | null = null;
  private mentionSearch$ = new Subject<string>();
  private mentionInputEl: HTMLInputElement | null = null;
  private mentionStartIndex: number | null = null;
  private mentionCaretIndex: number | null = null;
  private mentionCloseTimer: number | null = null;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private postService: PostService,
    private authService: AuthService,
    private fb: FormBuilder,
    private postDialogService: PostDialogService,
    private profileService: ProfileService
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
    this.mentionSearch$
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        switchMap((query) =>
          query.length < 2
            ? this.profileService.getMentionSuggestions(6)
            : this.profileService.searchUsers(query, 6)
        ),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (results) => {
          this.mentionResults = results || [];
          this.mentionIndex = 0;
          this.mentionOpen = this.mentionResults.length > 0;
        },
        error: () => {
          this.mentionResults = [];
          this.mentionOpen = false;
        }
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['autoOpenComments'] && this.autoOpenComments) {
      this.showComments = true;
    }
    if ((changes['focusCommentId'] && this.focusCommentId) || (changes['focusReplyId'] && this.focusReplyId)) {
      this.showComments = true;
    }
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
        comment.replies = [...comment.replies];
        this.post.comments = [...this.post.comments];
        this.refreshPostComments();
        this.cancelReply();
      },
      error: (err) => {
        console.error('Error adding reply', err);
        this.errorMessage = 'Failed to add reply. Please try again.';
      }
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

  toggleReplyReaction(reply: CommentResponse, emoji: string): void {
    if (!this.currentUsername || !reply || !reply.id) {
      return;
    }
    if (this.reactingReplyIds.has(reply.id)) {
      return;
    }
    this.reactingReplyIds.add(reply.id);
    this.postService.toggleReplyReaction(this.post.id, reply.id, emoji).subscribe({
      next: (summary) => {
        reply.reactions = summary.reactions || [];
        reply.viewerReaction = summary.viewerReaction ?? null;
        this.reactingReplyIds.delete(reply.id);
      },
      error: (err) => {
        console.error('Error reacting to reply', err);
        this.reactingReplyIds.delete(reply.id);
      }
    });
  }

  toggleReactionPicker(reply: CommentResponse): void {
    if (!reply || !reply.id) {
      return;
    }
    this.activeReactionPickerId = this.activeReactionPickerId === reply.id ? null : reply.id;
  }

  closeReactionPicker(): void {
    this.activeReactionPickerId = null;
  }

  getReactionSummary(reply: CommentResponse): string {
    if (!reply.reactions || reply.reactions.length === 0) {
      return '';
    }
    const sorted = [...reply.reactions].sort((a, b) => b.count - a.count);
    const top = sorted.slice(0, 2).map((item) => item.emoji).join(' ');
    const total = sorted.reduce((sum, item) => sum + item.count, 0);
    return `${top} ${total}`.trim();
  }

  hasReaction(reply: CommentResponse, emoji: string): boolean {
    return reply.viewerReaction === emoji;
  }

  getReactionCount(reply: CommentResponse, emoji: string): number {
    if (!reply.reactions) {
      return 0;
    }
    const match = reply.reactions.find((reaction) => reaction.emoji === emoji);
    return match ? match.count : 0;
  }

  onMentionInput(event: Event, inputType: 'comment' | 'reply'): void {
    const input = event.target as HTMLInputElement;
    if (!input) {
      return;
    }
    if (this.mentionCloseTimer !== null) {
      window.clearTimeout(this.mentionCloseTimer);
      this.mentionCloseTimer = null;
    }
    const caret = input.selectionStart ?? input.value.length;
    const mention = this.findMentionAtCaret(input.value, caret);
    if (!mention) {
      this.closeMentionPicker();
      return;
    }
    this.mentionActiveInput = inputType;
    this.mentionInputEl = input;
    this.mentionStartIndex = mention.start;
    this.mentionCaretIndex = caret;
    this.mentionSearch$.next(mention.query);
  }

  onMentionKeydown(event: KeyboardEvent): void {
    if (!this.mentionOpen) {
      return;
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.mentionIndex = Math.min(this.mentionIndex + 1, this.mentionResults.length - 1);
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.mentionIndex = Math.max(this.mentionIndex - 1, 0);
      return;
    }
    if (event.key === 'Enter') {
      const selected = this.mentionResults[this.mentionIndex];
      if (selected) {
        event.preventDefault();
        this.selectMention(selected);
      }
      return;
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeMentionPicker();
    }
  }

  onMentionBlur(): void {
    if (typeof window === 'undefined') {
      this.closeMentionPicker();
      return;
    }
    this.mentionCloseTimer = window.setTimeout(() => {
      this.closeMentionPicker();
    }, 150);
  }

  selectMention(user: UserSearchResult): void {
    if (!this.mentionInputEl || this.mentionStartIndex === null || this.mentionCaretIndex === null) {
      return;
    }
    const value = this.mentionInputEl.value;
    const before = value.slice(0, this.mentionStartIndex);
    const after = value.slice(this.mentionCaretIndex);
    const insert = `@${user.username} `;
    const nextValue = `${before}${insert}${after}`;
    this.mentionInputEl.value = nextValue;
    const nextCaret = before.length + insert.length;
    this.mentionInputEl.setSelectionRange(nextCaret, nextCaret);
    if (this.mentionActiveInput === 'comment') {
      this.commentForm.patchValue({ comment: nextValue });
    } else if (this.mentionActiveInput === 'reply') {
      this.replyForm.patchValue({ reply: nextValue });
    }
    this.closeMentionPicker();
  }

  isMentionActive(inputType: 'comment' | 'reply'): boolean {
    return this.mentionOpen && this.mentionActiveInput === inputType && this.mentionResults.length > 0;
  }

  private closeMentionPicker(): void {
    this.mentionOpen = false;
    this.mentionResults = [];
    this.mentionActiveInput = null;
    this.mentionInputEl = null;
    this.mentionStartIndex = null;
    this.mentionCaretIndex = null;
  }

  private findMentionAtCaret(value: string, caret: number): { start: number; query: string } | null {
    if (!value) {
      return null;
    }
    let index = caret - 1;
    while (index >= 0 && !/\s/.test(value[index])) {
      index--;
    }
    const wordStart = index + 1;
    const fragment = value.slice(wordStart, caret);
    if (!fragment.startsWith('@')) {
      return null;
    }
    const query = fragment.slice(1);
    return { start: wordStart, query };
  }

  toggleComments(): void {
    this.showComments = !this.showComments;
  }

  isFocusedComment(comment: CommentResponse): boolean {
    return this.focusCommentId === comment.id;
  }

  isFocusedReply(reply: CommentResponse): boolean {
    return this.focusReplyId === reply.id;
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

  private refreshPostComments(): void {
    this.postService.getPostById(this.post.id).subscribe({
      next: (updatedPost) => {
        this.post.comments = updatedPost.comments || [];
      },
      error: (err) => {
        console.error('Failed to refresh comments', err);
      }
    });
  }
}
