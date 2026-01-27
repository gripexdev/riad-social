import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './auth/auth.service';
import { NotificationService } from './notifications/notification.service';
import { ProfileService } from './profile/profile.service';

describe('AppComponent', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;
  let profileServiceSpy: jasmine.SpyObj<ProfileService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['isAuthenticated', 'removeToken', 'getUsername']);
    notificationServiceSpy = jasmine.createSpyObj<NotificationService>('NotificationService', ['getNotifications', 'getUnreadCount', 'markAllRead']);
    profileServiceSpy = jasmine.createSpyObj<ProfileService>('ProfileService', ['followUser', 'unfollowUser']);
    authServiceSpy.isAuthenticated.and.returnValue(false);
    authServiceSpy.getUsername.and.returnValue('user');

    await TestBed.configureTestingModule({
      imports: [AppComponent, RouterTestingModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: NotificationService, useValue: notificationServiceSpy },
        { provide: ProfileService, useValue: profileServiceSpy }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'riad' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('riad');
  });

  it('maps notification messages', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    expect(app.getNotificationMessage({ type: 'FOLLOW' } as any)).toBe('started following you.');
    expect(app.getNotificationMessage({ type: 'LIKE' } as any)).toBe('liked your post.');
    expect(app.getNotificationMessage({ type: 'COMMENT' } as any)).toBe('commented on your post.');
    expect(app.getNotificationMessage({ type: 'REPLY' } as any)).toBe('replied to your comment.');
    expect(app.getNotificationMessage({ type: 'UNKNOWN' } as any)).toBe('sent you a notification.');
  });

  it('computes relative time buckets', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    const now = Date.now();
    expect(app.getRelativeTime(new Date(now).toISOString())).toBe('just now');
    expect(app.getRelativeTime(new Date(now - 3 * 60 * 1000).toISOString())).toBe('3m');
    expect(app.getRelativeTime(new Date(now - 2 * 60 * 60 * 1000).toISOString())).toBe('2h');
  });

  it('formats unread badge counts', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.unreadCount = 0;
    expect(app.getUnreadBadge()).toBe('');
    app.unreadCount = 5;
    expect(app.getUnreadBadge()).toBe('5');
    app.unreadCount = 15;
    expect(app.getUnreadBadge()).toBe('9+');
  });

  it('responds to navigation end events', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    notificationServiceSpy.getUnreadCount.and.returnValue(of({ count: 0 }));
    const setIntervalSpy = spyOn(window, 'setInterval').and.returnValue(123 as unknown as number);

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    spyOn(app, 'closeNotifications');
    spyOn<any>(app, 'syncUnreadPolling').and.callThrough();

    app.ngOnInit();

    const router = TestBed.inject(Router);
    (router.events as any).next(new NavigationEnd(1, '/home', '/home'));

    expect(app.closeNotifications).toHaveBeenCalled();
    expect(setIntervalSpy).toHaveBeenCalled();
    app.ngOnDestroy();
  });

  it('closes notifications on escape when open', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.notificationsOpen = true;
    app.onEscape();

    expect(app.notificationsOpen).toBeFalse();
  });

  it('toggles notifications open and closed', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.notificationsOpen = true;
    app.toggleNotifications();
    expect(app.notificationsOpen).toBeFalse();

    spyOn(app, 'openNotifications').and.callThrough();
    app.toggleNotifications();
    expect(app.openNotifications).toHaveBeenCalled();
  });

  it('loads notifications when opened', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    notificationServiceSpy.getNotifications.and.returnValue(of([
      {
        id: 1,
        type: 'FOLLOW',
        actorUsername: 'alice',
        createdAt: new Date().toISOString(),
        isRead: false,
        actorFollowed: false
      }
    ]));
    notificationServiceSpy.markAllRead.and.returnValue(of(void 0));

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.openNotifications();

    expect(app.notificationsOpen).toBeTrue();
    expect(app.notificationsLoading).toBeFalse();
    expect(app.notifications.length).toBe(1);
    expect(notificationServiceSpy.markAllRead).toHaveBeenCalled();
  });

  it('skips loading notifications when unauthenticated', () => {
    authServiceSpy.isAuthenticated.and.returnValue(false);

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.loadNotifications();

    expect(notificationServiceSpy.getNotifications).not.toHaveBeenCalled();
  });

  it('loads notifications without marking read when none unread', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    notificationServiceSpy.getNotifications.and.returnValue(of([
      {
        id: 1,
        type: 'FOLLOW',
        actorUsername: 'alice',
        createdAt: new Date().toISOString(),
        isRead: true,
        actorFollowed: false
      }
    ]));

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.loadNotifications();

    expect(notificationServiceSpy.markAllRead).not.toHaveBeenCalled();
    expect(app.unreadCount).toBe(0);
  });

  it('logs errors when marking notifications read fails', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    notificationServiceSpy.getNotifications.and.returnValue(of([
      {
        id: 1,
        type: 'FOLLOW',
        actorUsername: 'alice',
        createdAt: new Date().toISOString(),
        isRead: false,
        actorFollowed: false
      }
    ]));
    notificationServiceSpy.markAllRead.and.returnValue(throwError(() => new Error('fail')));
    const consoleSpy = spyOn(console, 'error');

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.loadNotifications();

    expect(consoleSpy).toHaveBeenCalled();
  });

  it('handles notification load errors', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    notificationServiceSpy.getNotifications.and.returnValue(throwError(() => new Error('fail')));
    const consoleSpy = spyOn(console, 'error');

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.loadNotifications();

    expect(consoleSpy).toHaveBeenCalled();
    expect(app.notificationsLoading).toBeFalse();
    expect(app.notificationsError).toBe('Unable to load notifications.');
  });

  it('toggles follow state', () => {
    profileServiceSpy.followUser.and.returnValue(of(void 0));
    profileServiceSpy.unfollowUser.and.returnValue(of(void 0));

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    const notification: any = {
      id: 1,
      type: 'FOLLOW',
      actorUsername: 'alice',
      createdAt: new Date().toISOString(),
      isRead: false,
      actorFollowed: false
    };

    app.toggleFollow(notification);
    expect(profileServiceSpy.followUser).toHaveBeenCalledWith('alice');
    expect(notification.actorFollowed).toBeTrue();

    app.toggleFollow(notification);
    expect(profileServiceSpy.unfollowUser).toHaveBeenCalledWith('alice');
    expect(notification.actorFollowed).toBeFalse();
  });

  it('ignores follow toggles for non-follow notifications', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.toggleFollow({ type: 'LIKE' } as any);

    expect(profileServiceSpy.followUser).not.toHaveBeenCalled();
    expect(profileServiceSpy.unfollowUser).not.toHaveBeenCalled();
  });

  it('ignores follow toggles while a request is in flight', () => {
    const subject = new Subject<void>();
    profileServiceSpy.followUser.and.returnValue(subject.asObservable());

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    const notification: any = {
      id: 1,
      type: 'FOLLOW',
      actorUsername: 'alice',
      createdAt: new Date().toISOString(),
      isRead: false,
      actorFollowed: false
    };

    app.toggleFollow(notification);
    app.toggleFollow(notification);

    expect(profileServiceSpy.followUser).toHaveBeenCalledTimes(1);
    expect(app.isFollowLoading('alice')).toBeTrue();

    subject.next();
    subject.complete();
  });

  it('clears follow loading on error', () => {
    profileServiceSpy.followUser.and.returnValue(throwError(() => new Error('fail')));
    const consoleSpy = spyOn(console, 'error');

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    const notification: any = {
      id: 1,
      type: 'FOLLOW',
      actorUsername: 'alice',
      createdAt: new Date().toISOString(),
      isRead: false,
      actorFollowed: false
    };

    app.toggleFollow(notification);

    expect(consoleSpy).toHaveBeenCalled();
    expect(app.isFollowLoading('alice')).toBeFalse();
  });

  it('resets state on logout', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    app.notificationsOpen = true;
    app.notifications = [{ id: 1 } as any];
    app.recentNotifications = [{ id: 2 } as any];
    app.earlierNotifications = [{ id: 3 } as any];
    app.unreadCount = 7;

    app.logout();

    expect(authServiceSpy.removeToken).toHaveBeenCalled();
    expect(app.notificationsOpen).toBeFalse();
    expect(app.notifications.length).toBe(0);
    expect(app.unreadCount).toBe(0);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('computes longer relative time buckets', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    const now = Date.now();
    expect(app.getRelativeTime(new Date(now - 2 * 24 * 60 * 60 * 1000).toISOString())).toBe('2d');
    expect(app.getRelativeTime(new Date(now - 10 * 24 * 60 * 60 * 1000).toISOString())).toBe('1w');
    expect(app.getRelativeTime(new Date(now - 70 * 24 * 60 * 60 * 1000).toISOString())).toBe('2mo');
    expect(app.getRelativeTime(new Date(now - 400 * 24 * 60 * 60 * 1000).toISOString())).toBe('1y');
  });

  it('resets unread count when unauthenticated', () => {
    authServiceSpy.isAuthenticated.and.returnValue(false);

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.unreadCount = 3;

    (app as any).loadUnreadCount();

    expect(app.unreadCount).toBe(0);
  });

  it('logs unread count load errors', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    notificationServiceSpy.getUnreadCount.and.returnValue(throwError(() => new Error('fail')));
    const consoleSpy = spyOn(console, 'error');

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    (app as any).loadUnreadCount();

    expect(consoleSpy).toHaveBeenCalled();
  });

  it('stops unread polling when unauthenticated', () => {
    authServiceSpy.isAuthenticated.and.returnValue(false);
    const clearIntervalSpy = spyOn(window, 'clearInterval');

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    (app as any).unreadPollId = 101;

    (app as any).syncUnreadPolling();

    expect(clearIntervalSpy).toHaveBeenCalledWith(101);
  });

  it('returns early when polling already running', () => {
    const setIntervalSpy = spyOn(window, 'setInterval');

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    (app as any).unreadPollId = 101;

    (app as any).startUnreadPolling();

    expect(setIntervalSpy).not.toHaveBeenCalled();
  });

  it('starts and stops unread polling when authenticated', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    notificationServiceSpy.getUnreadCount.and.returnValue(of({ count: 2 }));
    const setIntervalSpy = spyOn(window, 'setInterval').and.callFake((handler: TimerHandler) => {
      if (typeof handler === 'function') {
        handler();
      }
      return 123 as unknown as number;
    });
    const clearIntervalSpy = spyOn(window, 'clearInterval');

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;

    app.ngOnInit();

    expect(setIntervalSpy).toHaveBeenCalled();
    expect(notificationServiceSpy.getUnreadCount).toHaveBeenCalled();

    app.ngOnDestroy();

    expect(clearIntervalSpy).toHaveBeenCalledWith(123);
  });
});
