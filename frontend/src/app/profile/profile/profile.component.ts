import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Profile, ProfileService } from '../profile.service';
import { AuthService } from '../../auth/auth.service';
import { CommonModule } from '@angular/common';
import { PostCardComponent } from '../../post/post-card/post-card.component';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, PostCardComponent],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss'
})
export class ProfileComponent implements OnInit {
  profile: Profile | null = null;
  currentUserUsername: string | null = null;
  isOwnProfile: boolean = false;

  constructor(
    private route: ActivatedRoute,
    private profileService: ProfileService,
    public authService: AuthService,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.currentUserUsername = this.authService.getUsername(); // Assuming getUsername() exists in AuthService

    this.route.paramMap.subscribe(params => {
      const username = params.get('username');
      if (username) {
        this.loadProfile(username);
        this.isOwnProfile = this.currentUserUsername === username;
      }
    });
  }

  loadProfile(username: string): void {
    this.profileService.getProfile(username).subscribe({
      next: (profile) => {
        this.profile = profile;
      },
      error: (err) => {
        console.error('Failed to load profile', err);
        this.router.navigate(['/home']); // Redirect if profile not found or error
      }
    });
  }

  onFollowToggle(): void {
    if (!this.profile) return;

    if (this.profile.isFollowing) {
      this.profileService.unfollowUser(this.profile.username).subscribe({
        next: () => {
          if (this.profile) {
            this.profile.isFollowing = false;
            this.profile.followerCount--;
          }
        },
        error: (err) => console.error('Failed to unfollow', err)
      });
    } else {
      this.profileService.followUser(this.profile.username).subscribe({
        next: () => {
          if (this.profile) {
            this.profile.isFollowing = true;
            this.profile.followerCount++;
          }
        },
        error: (err) => console.error('Failed to follow', err)
      });
    }
  }
}
