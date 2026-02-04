import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { HomeComponent } from './home.component';
import { PostService } from '../post/post.service';
import { AuthService } from '../auth/auth.service';
import { ProfileService } from '../profile/profile.service';

describe('HomeComponent', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let postService: jasmine.SpyObj<PostService>;
  let profileService: jasmine.SpyObj<ProfileService>;
  let queryParamMap$: Subject<any>;

  beforeEach(async () => {
    queryParamMap$ = new Subject<any>();
    postService = jasmine.createSpyObj<PostService>('PostService', ['getPosts', 'getPostById']);
    profileService = jasmine.createSpyObj<ProfileService>('ProfileService', ['getProfile']);
    postService.getPosts.and.returnValue(of([]));
    postService.getPostById.and.returnValue(of({ id: 1 } as any));
    profileService.getProfile.and.returnValue(of({
      username: 'alice',
      fullName: 'Alice',
      bio: '',
      profilePictureUrl: 'pic',
      postCount: 0,
      followerCount: 0,
      followingCount: 0,
      posts: [],
      isFollowing: false
    }));
    await TestBed.configureTestingModule({
      imports: [HomeComponent, HttpClientTestingModule, RouterTestingModule],
      providers: [
        { provide: PostService, useValue: postService },
        { provide: ProfileService, useValue: profileService },
        { provide: AuthService, useValue: { getUsername: () => 'alice' } },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParamMap$
          }
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    component.ngOnDestroy();
    queryParamMap$.complete();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads current user profile image', () => {
    expect(component.currentUserProfilePictureUrl).toBe('pic');
  });

  it('defaults profile image to null when missing', () => {
    profileService.getProfile.and.returnValue(of({
      username: 'alice',
      fullName: 'Alice',
      bio: '',
      profilePictureUrl: '',
      postCount: 0,
      followerCount: 0,
      followingCount: 0,
      posts: [],
      isFollowing: false
    }));
    (component as any).loadCurrentUserProfile();
    expect(component.currentUserProfilePictureUrl).toBeNull();
  });

  it('handles focused post query params', () => {
    queryParamMap$.next(convertToParamMap({ postId: '2', commentId: '3' }));
    expect(component.getFocusCommentId(2)).toBe(3);
  });

  it('fetches focused post when missing', () => {
    postService.getPostById.and.returnValue(of({ id: 2 } as any));
    component.focusedPostId = 2;
    (component as any).ensureFocusedPost();
    expect(postService.getPostById).toHaveBeenCalledWith(2);
  });

  it('removes post', () => {
    component.posts = [{ id: 1 } as any, { id: 2 } as any];
    component.removePost(1);
    expect(component.posts.length).toBe(1);
  });

  it('returns auto-open state', () => {
    component.autoOpenCommentPostId = 5;
    expect(component.getAutoOpenComments(5)).toBeTrue();
    expect(component.getAutoOpenComments(1)).toBeFalse();
  });

  it('returns focused reply id', () => {
    component.focusedPostId = 7;
    component.focusedReplyId = 9;
    expect(component.getFocusReplyId(7)).toBe(9);
  });

  it('returns null when focused ids do not match post', () => {
    component.focusedPostId = 2;
    component.focusedCommentId = 5;
    component.focusedReplyId = 6;
    expect(component.getFocusCommentId(3)).toBeNull();
    expect(component.getFocusReplyId(3)).toBeNull();
  });

  it('clears avatar when no username', () => {
    (component as any).authService.getUsername = () => null;
    (component as any).loadCurrentUserProfile();
    expect(component.currentUserProfilePictureUrl).toBeNull();
  });

  it('clears avatar when profile load fails', () => {
    component.currentUserProfilePictureUrl = 'pic';
    profileService.getProfile.and.returnValue(throwError(() => new Error('fail')));
    (component as any).loadCurrentUserProfile();
    expect(component.currentUserProfilePictureUrl).toBeNull();
  });

  it('parses invalid numbers safely', () => {
    const parseNumber = (component as any).parseNumber;
    expect(parseNumber.call(component, 'abc')).toBeNull();
  });

  it('parses valid numbers safely', () => {
    const parseNumber = (component as any).parseNumber;
    expect(parseNumber.call(component, '42')).toBe(42);
  });

  it('parses null numbers safely', () => {
    const parseNumber = (component as any).parseNumber;
    expect(parseNumber.call(component, null)).toBeNull();
  });

  it('clears auto open when no focused post', () => {
    component.focusedPostId = null;
    (component as any).ensureFocusedPost();
    expect(component.autoOpenCommentPostId).toBeNull();
  });

  it('triggerAutoOpen no-ops without focused post', () => {
    component.focusedPostId = null;
    (component as any).triggerAutoOpen();
    expect(component.autoOpenCommentPostId).toBeNull();
  });

  it('triggers auto open when focused post exists', () => {
    const timeoutSpy = spyOn(window, 'setTimeout').and.callFake((callback: any) => {
      callback();
      return 0 as any;
    });
    component.posts = [{ id: 3 } as any];
    component.focusedPostId = 3;

    (component as any).triggerAutoOpen();

    expect(timeoutSpy).toHaveBeenCalled();
    expect(component.autoOpenCommentPostId).toBe(3);
  });

  it('does not scroll when focus ids are missing', fakeAsync(() => {
    component.focusedPostId = 7;
    component.focusedCommentId = null;
    component.focusedReplyId = null;
    const getElementSpy = spyOn(document, 'getElementById');

    (component as any).scrollToFocused();
    tick(200);

    expect(getElementSpy).not.toHaveBeenCalled();
  }));

  it('does not scroll when focused post id is missing', fakeAsync(() => {
    component.focusedPostId = null;
    component.focusedCommentId = 3;
    const getElementSpy = spyOn(document, 'getElementById');

    (component as any).scrollToFocused();
    tick(200);

    expect(getElementSpy).not.toHaveBeenCalled();
  }));

  it('uses existing post to trigger auto open', () => {
    component.posts = [{ id: 10 } as any];
    component.focusedPostId = 10;
    const triggerSpy = spyOn(component as any, 'triggerAutoOpen');

    (component as any).ensureFocusedPost();

    expect(triggerSpy).toHaveBeenCalled();
  });

  it('sets fetch flags on focused post error', () => {
    postService.getPostById.and.returnValue(throwError(() => new Error('fail')));
    component.focusedPostId = 44;

    (component as any).ensureFocusedPost();

    expect((component as any).fetchingFocusedPost).toBeFalse();
    expect((component as any).lastFetchedPostId).toBe(44);
  });

  it('sets fetch flags on focused post success', () => {
    postService.getPostById.and.returnValue(of({ id: 12 } as any));
    component.focusedPostId = 12;

    (component as any).ensureFocusedPost();

    expect((component as any).fetchingFocusedPost).toBeFalse();
    expect((component as any).lastFetchedPostId).toBe(12);
  });

  it('scrolls to focused element when available', fakeAsync(() => {
    component.focusedPostId = 7;
    component.focusedCommentId = 9;
    const scrollSpy = jasmine.createSpy('scrollIntoView');
    const getElementSpy = spyOn(document, 'getElementById').and.returnValue({ scrollIntoView: scrollSpy } as any);

    (component as any).scrollToFocused();
    tick(150);

    expect(getElementSpy).toHaveBeenCalledWith('comment-9');
    expect(scrollSpy).toHaveBeenCalled();
  }));

  it('retries scroll when element missing', fakeAsync(() => {
    component.focusedPostId = 7;
    component.focusedReplyId = 11;
    spyOn(document, 'getElementById').and.returnValue(null);
    const retrySpy = spyOn(component as any, 'scrollToFocused').and.callThrough();

    (component as any).scrollToFocused();
    tick(150);

    expect(retrySpy).toHaveBeenCalledWith(1);
  }));

  it('does not refetch focused post when already fetching', () => {
    component.focusedPostId = 2;
    (component as any).fetchingFocusedPost = true;
    (component as any).ensureFocusedPost();
    expect(postService.getPostById).not.toHaveBeenCalled();
  });

  it('does not refetch focused post when already attempted', () => {
    component.focusedPostId = 2;
    (component as any).lastFetchedPostId = 2;
    (component as any).ensureFocusedPost();
    expect(postService.getPostById).not.toHaveBeenCalled();
  });
});
