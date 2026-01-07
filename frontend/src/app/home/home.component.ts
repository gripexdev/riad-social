import { Component, OnInit } from '@angular/core';
import { Post, PostService } from '../post/post.service';
import { PostCardComponent } from '../post/post-card/post-card.component';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { ProfileService } from '../profile/profile.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, PostCardComponent, RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  posts: Post[] = [];
  currentUserProfilePictureUrl: string | null = null;

  constructor(
    private postService: PostService,
    private authService: AuthService,
    private profileService: ProfileService
  ) { }

  ngOnInit(): void {
    this.postService.getPosts().subscribe(posts => {
      this.posts = posts;
    });
    this.loadCurrentUserProfile();
  }

  get currentUsername(): string | null {
    return this.authService.getUsername();
  }

  removePost(postId: number): void {
    this.posts = this.posts.filter(post => post.id !== postId);
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
}
