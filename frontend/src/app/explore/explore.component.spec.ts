import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { ExploreComponent } from './explore.component';
import { Post, PostService } from '../post/post.service';

describe('ExploreComponent', () => {
  it('loads explore posts on init', async () => {
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

    const postServiceSpy = jasmine.createSpyObj<PostService>('PostService', ['getExplorePosts']);
    postServiceSpy.getExplorePosts.and.returnValue(of(posts));

    await TestBed.configureTestingModule({
      imports: [ExploreComponent, RouterTestingModule, HttpClientTestingModule],
      providers: [{ provide: PostService, useValue: postServiceSpy }]
    }).compileComponents();

    const fixture = TestBed.createComponent(ExploreComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.posts).toEqual(posts);
  });
});
