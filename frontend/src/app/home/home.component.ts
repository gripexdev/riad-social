import { Component, OnDestroy, OnInit } from '@angular/core';
import { Post, PostService } from '../post/post.service';
import { PostCardComponent } from '../post/post-card/post-card.component';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { ProfileService } from '../profile/profile.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, PostCardComponent, RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit, OnDestroy {
  posts: Post[] = [];
  currentUserProfilePictureUrl: string | null = null;
  focusedPostId: number | null = null;
  focusedCommentId: number | null = null;
  focusedReplyId: number | null = null;
  autoOpenCommentPostId: number | null = null;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private postService: PostService,
    private authService: AuthService,
    private profileService: ProfileService,
    private route: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntil(this.destroy$))
      .subscribe((params) => {
        this.focusedPostId = this.parseNumber(params.get('postId'));
        this.focusedCommentId = this.parseNumber(params.get('commentId'));
        this.focusedReplyId = this.parseNumber(params.get('replyId'));
        this.autoOpenCommentPostId = this.focusedPostId;
        this.scrollToFocused();
      });

    this.postService.getPosts().subscribe(posts => {
      this.posts = posts;
      this.autoOpenCommentPostId = this.focusedPostId;
      this.scrollToFocused();
    });
    this.loadCurrentUserProfile();
  }

  get currentUsername(): string | null {
    return this.authService.getUsername();
  }

  removePost(postId: number): void {
    this.posts = this.posts.filter(post => post.id !== postId);
  }

  getAutoOpenComments(postId: number): boolean {
    return this.autoOpenCommentPostId === postId;
  }

  getFocusCommentId(postId: number): number | null {
    return this.focusedPostId === postId ? this.focusedCommentId : null;
  }

  getFocusReplyId(postId: number): number | null {
    return this.focusedPostId === postId ? this.focusedReplyId : null;
  }

  private loadCurrentUserProfile(): void {
    const username = this.currentUsername;
    if (!username) {
      this.currentUserProfilePictureUrl = null;
      return;
    }
    this.profileService.getProfile(username).subscribe({
      next: (profile) => {
        this.currentUserProfilePictureUrl = profile.profilePictureUrl || null;
      },
      error: () => {
        this.currentUserProfilePictureUrl = null;
      }
    });
  }

  private parseNumber(value: string | null): number | null {
    if (!value) {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private scrollToFocused(): void {
    if (!this.focusedPostId) {
      return;
    }
    const targetId = this.focusedReplyId
      ? `reply-${this.focusedReplyId}`
      : this.focusedCommentId
        ? `comment-${this.focusedCommentId}`
        : null;
    if (!targetId) {
      return;
    }
    if (typeof document === 'undefined') {
      return;
    }
    setTimeout(() => {
      const element = document.getElementById(targetId);
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }, 50);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
