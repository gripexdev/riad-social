import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Post, PostService } from './post.service';

describe('PostService', () => {
  let service: PostService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(PostService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should request explore posts', () => {
    const posts: Post[] = [
      {
        id: 1,
        imageUrl: 'image',
        caption: 'caption',
        username: 'owner',
        createdAt: new Date().toISOString(),
        likesCount: 0,
        likedByCurrentUser: false,
        comments: []
      }
    ];

    service.getExplorePosts().subscribe((response) => {
      expect(response).toEqual(posts);
    });

    const req = httpMock.expectOne('http://localhost:8080/api/posts/explore');
    expect(req.request.method).toBe('GET');
    req.flush(posts);
  });
});
