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

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('requests profile', () => {
    service.getProfile('alice').subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/users/alice');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('requests mention suggestions', () => {
    service.getMentionSuggestions(4).subscribe();
    const req = httpMock.expectOne((request) => request.url.endsWith('/mentions'));
    expect(req.request.params.get('limit')).toBe('4');
    req.flush([]);
  });
});
