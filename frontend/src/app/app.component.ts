import { Component, HostBinding, HostListener, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './auth/auth.service';
import { CommonModule } from '@angular/common';
import { AppNotification, NotificationService } from './notifications/notification.service';
import { NotificationRealtimeService } from './notifications/notification-realtime.service';
import { ProfileService } from './profile/profile.service';
import { Subject, Subscription } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'riad';
  @HostBinding('class.messages-route') isMessagesRoute = false;
  @HostBinding('class.notifications-open') get notificationsOpenClass(): boolean {
    return this.notificationsOpen;
  }
  notificationsOpen = false;
  notificationsLoading = false;
  notificationsError: string | null = null;
  notifications: AppNotification[] = [];
  recentNotifications: AppNotification[] = [];
  earlierNotifications: AppNotification[] = [];
  unreadCount = 0;
  private readonly followRequestUsernames = new Set<string>();
  private unreadPollId: number | null = null;
  private notificationRealtimeSubscription: Subscription | null = null;
  private readonly destroy$ = new Subject<void>();

  constructor(
    public authService: AuthService,
    private router: Router,
    private notificationService: NotificationService,
    private notificationRealtimeService: NotificationRealtimeService,
    private profileService: ProfileService
  ) {}

  ngOnInit(): void {
    this.closeNotifications();
    this.syncNotificationRealtime();
    this.syncUnreadPolling();
    this.updateRouteState(this.router.url);
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        this.closeNotifications();
        this.syncNotificationRealtime();
        this.syncUnreadPolling();
        this.updateRouteState(this.router.url);
      });
  }

  logout(): void {
    this.authService.removeToken();
    this.closeNotifications();
    this.stopNotificationRealtime();
    this.stopUnreadPolling();
    this.notifications = [];
    this.recentNotifications = [];
    this.earlierNotifications = [];
    this.unreadCount = 0;
    this.router.navigate(['/login']);
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEscape(): void {
    if (this.notificationsOpen) {
      this.closeNotifications();
    }
  }

  toggleNotifications(): void {
    if (this.notificationsOpen) {
      this.closeNotifications();
      return;
    }
    this.openNotifications();
  }

  openNotifications(): void {
    this.notificationsOpen = true;
    this.loadNotifications();
  }

  closeNotifications(): void {
    this.notificationsOpen = false;
  }

  loadNotifications(): void {
    if (!this.authService.isAuthenticated()) {
      return;
    }
    this.notificationsLoading = true;
    this.notificationsError = null;
    this.notificationService.getNotifications().subscribe({
      next: (notifications) => {
        this.notifications = notifications || [];
        this.splitNotifications();
        this.notificationsLoading = false;
        if (this.notifications.some((notification) => !notification.isRead)) {
          this.notificationService.markAllRead().subscribe({
            next: () => {
              this.notifications = this.notifications.map((notification) => ({
                ...notification,
                isRead: true
              }));
              this.splitNotifications();
              this.unreadCount = 0;
            },
            error: (err) => {
              console.error('Failed to mark notifications read', err);
            }
          });
        } else {
          this.unreadCount = 0;
        }
      },
      error: (err) => {
        console.error('Failed to load notifications', err);
        this.notificationsLoading = false;
        this.notificationsError = 'Unable to load notifications.';
      }
    });
  }

  getNotificationMessage(notification: AppNotification): string {
    switch (notification.type) {
      case 'FOLLOW':
        return 'started following you.';
      case 'LIKE':
        return 'liked your post.';
      case 'COMMENT':
        return 'commented on your post.';
      case 'REPLY':
        return 'replied to your comment.';
      case 'MENTION':
        return 'mentioned you on a post.';
      default:
        return 'sent you a notification.';
    }
  }

  openNotification(notification: AppNotification): void {
    if (notification.type === 'FOLLOW') {
      this.router.navigate(['/users', notification.actorUsername]);
      this.closeNotifications();
      return;
    }
    if (notification.postId) {
      const queryParams: Record<string, number> = { postId: notification.postId };
      if (notification.parentCommentId && notification.commentId) {
        queryParams['commentId'] = notification.parentCommentId;
        queryParams['replyId'] = notification.commentId;
      } else if (notification.commentId) {
        queryParams['commentId'] = notification.commentId;
      }
      this.router.navigate(['/home'], { queryParams });
      this.closeNotifications();
    }
  }

  getRelativeTime(createdAt: string): string {
    const date = new Date(createdAt);
    const diffMs = Date.now() - date.getTime();
    const minute = 60 * 1000;
    const hour = 60 * minute;
    const day = 24 * hour;
    const week = 7 * day;
    if (diffMs < minute) {
      return 'just now';
    }
    if (diffMs < hour) {
      return `${Math.floor(diffMs / minute)}m`;
    }
    if (diffMs < day) {
      return `${Math.floor(diffMs / hour)}h`;
    }
    if (diffMs < week) {
      return `${Math.floor(diffMs / day)}d`;
    }
    const weeks = Math.floor(diffMs / week);
    if (weeks < 4) {
      return `${weeks}w`;
    }
    const months = Math.floor(diffMs / (30 * day));
    if (months < 12) {
      return `${months}mo`;
    }
    const years = Math.floor(diffMs / (365 * day));
    return `${years}y`;
  }

  getUnreadBadge(): string {
    if (this.unreadCount <= 0) {
      return '';
    }
    return this.unreadCount > 9 ? '9+' : `${this.unreadCount}`;
  }

  toggleFollow(notification: AppNotification): void {
    if (notification.type !== 'FOLLOW') {
      return;
    }
    const username = notification.actorUsername;
    if (this.followRequestUsernames.has(username)) {
      return;
    }
    this.followRequestUsernames.add(username);
    const request$ = notification.actorFollowed
      ? this.profileService.unfollowUser(username)
      : this.profileService.followUser(username);
    request$.subscribe({
      next: () => {
        notification.actorFollowed = !notification.actorFollowed;
        this.followRequestUsernames.delete(username);
      },
      error: (err) => {
        console.error('Failed to toggle follow', err);
        this.followRequestUsernames.delete(username);
      }
    });
  }

  isFollowLoading(username: string): boolean {
    return this.followRequestUsernames.has(username);
  }

  private splitNotifications(): void {
    const now = Date.now();
    const weekMs = 7 * 24 * 60 * 60 * 1000;
    this.recentNotifications = this.notifications.filter((notification) => {
      const createdAtMs = new Date(notification.createdAt).getTime();
      return now - createdAtMs <= weekMs;
    });
    this.earlierNotifications = this.notifications.filter((notification) => {
      const createdAtMs = new Date(notification.createdAt).getTime();
      return now - createdAtMs > weekMs;
    });
  }

  private loadUnreadCount(): void {
    if (!this.authService.isAuthenticated()) {
      this.unreadCount = 0;
      return;
    }
    this.notificationService.getUnreadCount().subscribe({
      next: (response) => {
        this.unreadCount = response.count;
      },
      error: (err) => {
        console.error('Failed to load unread notification count', err);
      }
    });
  }

  private syncUnreadPolling(): void {
    if (this.authService.isAuthenticated()) {
      this.startUnreadPolling();
    } else {
      this.stopUnreadPolling();
    }
  }

  private syncNotificationRealtime(): void {
    if (this.authService.isAuthenticated()) {
      this.startNotificationRealtime();
    } else {
      this.stopNotificationRealtime();
    }
  }

  private startUnreadPolling(): void {
    if (this.unreadPollId !== null) {
      return;
    }
    this.loadUnreadCount();
    if (typeof window === 'undefined') {
      return;
    }
    this.unreadPollId = window.setInterval(() => {
      this.loadUnreadCount();
    }, 30000);
  }

  private stopUnreadPolling(): void {
    if (this.unreadPollId === null) {
      return;
    }
    window.clearInterval(this.unreadPollId);
    this.unreadPollId = null;
  }

  private startNotificationRealtime(): void {
    if (this.notificationRealtimeSubscription) {
      return;
    }
    this.notificationRealtimeService.connect();
    this.notificationRealtimeSubscription = this.notificationRealtimeService.onCount().subscribe({
      next: (count) => {
        this.unreadCount = count;
      },
      error: (err) => {
        console.error('Failed to receive realtime notification count', err);
      }
    });
  }

  private stopNotificationRealtime(): void {
    if (this.notificationRealtimeSubscription) {
      this.notificationRealtimeSubscription.unsubscribe();
      this.notificationRealtimeSubscription = null;
    }
    this.notificationRealtimeService.disconnect();
  }

  private updateRouteState(url: string): void {
    this.isMessagesRoute = url.startsWith('/messages');
  }

  ngOnDestroy(): void {
    this.stopNotificationRealtime();
    this.stopUnreadPolling();
    this.destroy$.next();
    this.destroy$.complete();
  }
}
