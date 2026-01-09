import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, finalize, map, switchMap, takeUntil, tap } from 'rxjs/operators';
import { ProfileService, UserSearchResult } from '../profile/profile.service';

@Component({
  selector: 'app-user-search',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink
  ],
  templateUrl: './user-search.component.html',
  styleUrl: './user-search.component.scss'
})
export class UserSearchComponent implements OnInit, OnDestroy {
  searchControl!: FormControl<string>;
  results: UserSearchResult[] = [];
  searchTerm = '';
  isLoading = false;
  errorMessage: string | null = null;
  // Track follow/unfollow requests to avoid double clicks.
  private pendingUsernames = new Set<string>();
  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private profileService: ProfileService
  ) {
    // Initialize after DI is ready to satisfy strict property checks.
    this.searchControl = this.fb.control('', { nonNullable: true });
  }

  ngOnInit(): void {
    // Debounce input so we don't spam the backend on every keystroke.
    this.searchControl.valueChanges.pipe(
      map(value => (value ?? '').toString().trim()),
      debounceTime(300),
      distinctUntilChanged(),
      tap(query => {
        this.searchTerm = query;
        this.errorMessage = null;
        if (query.length < 2) {
          this.results = [];
        }
      }),
      switchMap(query => {
        if (query.length < 2) {
          return of([]);
        }
        this.isLoading = true;
        return this.profileService.searchUsers(query).pipe(
          catchError(error => {
            console.error('Search failed', error);
            this.errorMessage = 'Failed to search users. Please try again.';
            return of([]);
          }),
          finalize(() => {
            this.isLoading = false;
          })
        );
      }),
      takeUntil(this.destroy$)
    ).subscribe(results => {
      this.results = results;
    });
  }

  toggleFollow(user: UserSearchResult): void {
    if (this.pendingUsernames.has(user.username)) {
      return;
    }
    this.pendingUsernames.add(user.username);

    const request$ = user.isFollowing
      ? this.profileService.unfollowUser(user.username)
      : this.profileService.followUser(user.username);

    request$.subscribe({
      next: () => {
        user.isFollowing = !user.isFollowing;
        this.pendingUsernames.delete(user.username);
      },
      error: (error) => {
        console.error('Failed to update follow status', error);
        this.errorMessage = 'Failed to update follow status. Please try again.';
        this.pendingUsernames.delete(user.username);
      }
    });
  }

  isPending(username: string): boolean {
    return this.pendingUsernames.has(username);
  }

  trackByUsername(_: number, user: UserSearchResult): string {
    return user.username;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
