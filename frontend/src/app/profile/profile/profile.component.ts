import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Profile, ProfileService } from '../profile.service';
import { AuthService } from '../../auth/auth.service';
import { CommonModule } from '@angular/common';
import { PostCardComponent } from '../../post/post-card/post-card.component';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, PostCardComponent, ReactiveFormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss'
})
export class ProfileComponent implements OnInit, OnDestroy {
  profile: Profile | null = null;
  currentUserUsername: string | null = null;
  isOwnProfile: boolean = false;
  isEditing: boolean = false;
  profileForm!: FormGroup;
  selectedAvatar: File | null = null;
  avatarPreviewUrl: string | null = null;
  errorMessage: string | null = null;
  isSaving: boolean = false;
  private readonly maxFileSizeBytes = 10 * 1024 * 1024;

  @ViewChild('avatarInput') avatarInput?: ElementRef<HTMLInputElement>;

  constructor(
    private route: ActivatedRoute,
    private profileService: ProfileService,
    public authService: AuthService,
    private router: Router,
    private fb: FormBuilder
  ) { }

  ngOnInit(): void {
    this.currentUserUsername = this.authService.getUsername(); // Assuming getUsername() exists in AuthService
    this.profileForm = this.fb.group({
      bio: ['']
    });

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
        this.profileForm.patchValue({ bio: profile.bio || '' });
        this.isEditing = false;
        this.clearAvatarPreview();
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

  startEdit(): void {
    if (!this.profile) return;
    this.isEditing = true;
    this.errorMessage = null;
    this.profileForm.patchValue({ bio: this.profile.bio || '' });
  }

  cancelEdit(): void {
    if (!this.profile) return;
    this.isEditing = false;
    this.errorMessage = null;
    this.profileForm.patchValue({ bio: this.profile.bio || '' });
    this.clearAvatarPreview();
  }

  onAvatarSelected(event: Event): void {
    const element = event.currentTarget as HTMLInputElement;
    const fileList: FileList | null = element.files;
    this.errorMessage = null;
    if (fileList && fileList.length > 0) {
      const file = fileList[0];
      if (!file.type.startsWith('image/')) {
        this.clearAvatarPreview();
        this.errorMessage = 'Please choose an image file.';
        return;
      }
      if (file.size > this.maxFileSizeBytes) {
        this.clearAvatarPreview();
        this.errorMessage = 'Image is too large. Max size is 10MB.';
        return;
      }
      this.selectedAvatar = file;
      this.setAvatarPreview(file);
    }
  }

  saveProfile(): void {
    if (!this.profile) return;
    this.isSaving = true;
    this.errorMessage = null;
    const bio = (this.profileForm.value.bio || '').trim();
    this.profileService.updateProfile(bio, this.selectedAvatar).subscribe({
      next: (updatedProfile) => {
        this.profile = updatedProfile;
        this.isEditing = false;
        this.isSaving = false;
        this.clearAvatarPreview();
      },
      error: (err) => {
        console.error('Failed to update profile', err);
        this.isSaving = false;
        this.errorMessage = 'Failed to update profile. Please try again.';
      }
    });
  }

  get displayAvatarUrl(): string {
    if (this.avatarPreviewUrl) {
      return this.avatarPreviewUrl;
    }
    return this.profile?.profilePictureUrl || 'https://via.placeholder.com/150';
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    const kb = bytes / 1024;
    if (kb < 1024) {
      return `${kb.toFixed(1)} KB`;
    }
    const mb = kb / 1024;
    return `${mb.toFixed(1)} MB`;
  }

  ngOnDestroy(): void {
    this.clearAvatarPreview();
  }

  private setAvatarPreview(file: File): void {
    if (this.avatarPreviewUrl) {
      URL.revokeObjectURL(this.avatarPreviewUrl);
    }
    this.avatarPreviewUrl = URL.createObjectURL(file);
  }

  private clearAvatarPreview(): void {
    this.selectedAvatar = null;
    if (this.avatarInput?.nativeElement) {
      this.avatarInput.nativeElement.value = '';
    }
    if (this.avatarPreviewUrl) {
      URL.revokeObjectURL(this.avatarPreviewUrl);
      this.avatarPreviewUrl = null;
    }
  }
}
