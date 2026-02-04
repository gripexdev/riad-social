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

  it('requests posts feed', () => {
    service.getPosts().subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/posts');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('requests post by id', () => {
    service.getPostById(4).subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/posts/4');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('adds comment', () => {
    service.addComment(1, 'hello', 2).subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/posts/1/comment');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ content: 'hello', parentCommentId: 2 });
    req.flush({});
  });

  it('toggles reply reaction', () => {
    service.toggleReplyReaction(1, 3, 'ðŸ‘').subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/posts/1/comments/3/reactions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ emoji: 'ðŸ‘' });
    req.flush({});
  });
});
