import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, Subject } from 'rxjs';
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

  it('clears avatar when no username', () => {
    (component as any).authService.getUsername = () => null;
    (component as any).loadCurrentUserProfile();
    expect(component.currentUserProfilePictureUrl).toBeNull();
  });

  it('parses invalid numbers safely', () => {
    const parseNumber = (component as any).parseNumber;
    expect(parseNumber.call(component, 'abc')).toBeNull();
  });

  it('clears auto open when no focused post', () => {
    component.focusedPostId = null;
    (component as any).ensureFocusedPost();
    expect(component.autoOpenCommentPostId).toBeNull();
  });

  it('triggers auto open when focused post exists', fakeAsync(() => {
    component.posts = [{ id: 3 } as any];
    component.focusedPostId = 3;
    (component as any).triggerAutoOpen();
    tick(0);
    expect(component.autoOpenCommentPostId).toBe(3);
  }));
});
