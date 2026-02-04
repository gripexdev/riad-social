import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { ProfileComponent } from './profile.component';
import { ProfileService } from '../profile.service';
import { AuthService } from '../../auth/auth.service';

describe('ProfileComponent', () => {
  let component: ProfileComponent;
  let fixture: ComponentFixture<ProfileComponent>;
  let profileService: jasmine.SpyObj<ProfileService>;
  let router: Router;

  beforeEach(async () => {
    profileService = jasmine.createSpyObj<ProfileService>('ProfileService', [
      'getProfile',
      'followUser',
      'unfollowUser',
      'updateProfile'
    ]);
    profileService.getProfile.and.returnValue(of({
      username: 'alice',
      fullName: 'Alice',
      bio: 'bio',
      profilePictureUrl: '',
      postCount: 0,
      followerCount: 0,
      followingCount: 0,
      posts: [],
      isFollowing: false
    }));
    profileService.updateProfile.and.returnValue(of({
      username: 'alice',
      fullName: 'Alice',
      bio: 'bio',
      profilePictureUrl: '',
      postCount: 0,
      followerCount: 0,
      followingCount: 0,
      posts: [],
      isFollowing: false
    }));
    await TestBed.configureTestingModule({
      imports: [ProfileComponent, HttpClientTestingModule, RouterTestingModule],
      providers: [
        { provide: ProfileService, useValue: profileService },
        { provide: AuthService, useValue: { getUsername: () => 'alice' } },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ username: 'alice' }))
          }
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('formats file sizes', () => {
    expect(component.formatFileSize(500)).toBe('500 B');
    expect(component.formatFileSize(2048)).toContain('KB');
    expect(component.formatFileSize(5 * 1024 * 1024)).toContain('MB');
  });

  it('sets profile initial', () => {
    component.profile = {
      username: 'bob',
      fullName: '',
      bio: '',
      profilePictureUrl: '',
      postCount: 0,
      followerCount: 0,
      followingCount: 0,
      posts: [],
      isFollowing: false
    };
    expect(component.profileInitial).toBe('B');
  });

  it('navigates away when profile load fails', () => {
    spyOn(router, 'navigate');
    profileService.getProfile.and.returnValue(throwError(() => new Error('fail')));
    component.loadProfile('bob');
    expect(router.navigate).toHaveBeenCalledWith(['/home']);
  });

  it('rejects non-image avatar', () => {
    const file = new File(['test'], 'test.txt', { type: 'text/plain' });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [file] });
    component.onAvatarSelected({ currentTarget: input } as any);
    expect(component.errorMessage).toContain('image');
  });

  it('saves profile', () => {
    component.profile = {
      username: 'alice',
      fullName: 'Alice',
      bio: 'bio',
      profilePictureUrl: '',
      postCount: 0,
      followerCount: 0,
      followingCount: 0,
      posts: [],
      isFollowing: false
    };
    component.profileForm.setValue({ bio: 'hello' });
    component.saveProfile();
    expect(profileService.updateProfile).toHaveBeenCalled();
  });

  it('toggles follow state', () => {
    component.profile = {
      username: 'bob',
      fullName: 'Bob',
      bio: '',
      profilePictureUrl: '',
      postCount: 0,
      followerCount: 1,
      followingCount: 0,
      posts: [],
      isFollowing: false
    };
    profileService.followUser.and.returnValue(of(void 0));
    component.onFollowToggle();
    expect(component.profile.isFollowing).toBeTrue();
  });
});
