import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ProfileService } from './profile.service';

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(ProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('gets profile', () => {
    service.getProfile('alice').subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/users/alice');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('follows and unfollows user', () => {
    service.followUser('bob').subscribe();
    const followReq = httpMock.expectOne('http://localhost:8080/api/users/bob/follow');
    expect(followReq.request.method).toBe('POST');
    followReq.flush({});

    service.unfollowUser('bob').subscribe();
    const unfollowReq = httpMock.expectOne('http://localhost:8080/api/users/bob/unfollow');
    expect(unfollowReq.request.method).toBe('POST');
    unfollowReq.flush({});
  });

  it('updates profile with form data', () => {
    const avatar = new File(['x'], 'avatar.png', { type: 'image/png' });
    service.updateProfile('bio', avatar).subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/users/me');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body instanceof FormData).toBeTrue();
    req.flush({});
  });

  it('searches users and fetches mention suggestions', () => {
    service.searchUsers('al', 5).subscribe();
    const searchReq = httpMock.expectOne((request) =>
      request.url === 'http://localhost:8080/api/users/search'
    );
    expect(searchReq.request.params.get('q')).toBe('al');
    expect(searchReq.request.params.get('limit')).toBe('5');
    searchReq.flush([]);

    service.getMentionSuggestions(4).subscribe();
    const mentionReq = httpMock.expectOne((request) =>
      request.url === 'http://localhost:8080/api/users/mentions'
    );
    expect(mentionReq.request.params.get('limit')).toBe('4');
    mentionReq.flush([]);
  });

  it('uses default limit for mention suggestions', () => {
    service.getMentionSuggestions().subscribe();
    const req = httpMock.expectOne((request) =>
      request.url === 'http://localhost:8080/api/users/mentions'
    );
    expect(req.request.params.get('limit')).toBe('6');
    req.flush([]);
  });
});
