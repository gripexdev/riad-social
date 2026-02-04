import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { OverlayModule } from '@angular/cdk/overlay';
import { PostCardComponent } from './post-card.component';
import { PostService } from '../post.service';
import { AuthService } from '../../auth/auth.service';
import { PostDialogService } from '../post-dialog.service';
import { ProfileService } from '../../profile/profile.service';

describe('PostCardComponent', () => {
  let component: PostCardComponent;
  let fixture: ComponentFixture<PostCardComponent>;
  let postService: jasmine.SpyObj<PostService>;

  beforeEach(async () => {
    postService = jasmine.createSpyObj<PostService>('PostService', [
      'addComment',
      'toggleLike',
      'toggleReplyReaction',
      'getPostById',
      'deleteComment',
      'updatePost',
      'deletePost'
    ]);
    postService.addComment.and.returnValue(of({
      id: 2,
      content: 'reply',
      username: 'me',
      createdAt: new Date().toISOString(),
      parentId: 1,
      replies: []
    }));
    postService.toggleLike.and.returnValue(of({
      id: 1,
      imageUrl: 'image',
      caption: 'caption',
      username: 'owner',
      createdAt: new Date().toISOString(),
      likesCount: 1,
      likedByCurrentUser: true,
      comments: []
    }));
    postService.getPostById.and.returnValue(of({
      id: 1,
      imageUrl: 'image',
      caption: 'caption',
      username: 'owner',
      createdAt: new Date().toISOString(),
      likesCount: 0,
      likedByCurrentUser: false,
      comments: []
    }));
    postService.deleteComment.and.returnValue(of(void 0));
    await TestBed.configureTestingModule({
      imports: [PostCardComponent, HttpClientTestingModule, RouterTestingModule, OverlayModule],
      providers: [
        { provide: PostService, useValue: postService },
        { provide: AuthService, useValue: { getUsername: () => 'me' } },
        { provide: PostDialogService, useValue: { activeDeletePostId$: of(null), openDelete: () => {}, closeDelete: () => {} } },
        { provide: ProfileService, useValue: { getMentionSuggestions: () => of([]), searchUsers: () => of([]) } }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PostCardComponent);
    component = fixture.componentInstance;
    component.post = {
      id: 1,
      imageUrl: 'image',
      caption: 'caption',
      username: 'owner',
      createdAt: new Date().toISOString(),
      likesCount: 0,
      likedByCurrentUser: false,
      comments: []
    };
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('parses mentions in content', () => {
    const parts = component.parseContent('hello @alice');
    expect(parts.length).toBe(2);
    expect(parts[1].isMention).toBeTrue();
    expect(parts[1].username).toBe('alice');
  });

  it('toggles replies expansion', () => {
    const comment = { id: 1, content: 'hi', username: 'bob', createdAt: new Date().toISOString() } as any;
    expect(component.isRepliesExpanded(comment)).toBeFalse();
    component.toggleReplies(comment);
    expect(component.isRepliesExpanded(comment)).toBeTrue();
  });

  it('starts and cancels reply', () => {
    const comment = { id: 1, content: 'hi', username: 'bob', createdAt: new Date().toISOString() } as any;
    component.startReply(comment);
    expect(component.activeReplyCommentId).toBe(1);
    component.cancelReply();
    expect(component.activeReplyCommentId).toBeNull();
  });

  it('adds reply and clears form', () => {
    const comment = { id: 1, content: 'hi', username: 'bob', createdAt: new Date().toISOString(), replies: [] } as any;
    component.startReply(comment);
    component.replyForm.setValue({ reply: 'reply' });
    component.addReply(comment);
    expect(postService.addComment).toHaveBeenCalled();
  });

  it('toggles like updates post', () => {
    component.toggleLike();
    expect(postService.toggleLike).toHaveBeenCalled();
  });

  it('adds comment to list', () => {
    component.commentForm.setValue({ comment: 'hello' });
    component.addComment();
    expect(postService.addComment).toHaveBeenCalled();
  });

  it('deletes reply', () => {
    const parent = { id: 1, replies: [{ id: 2, username: 'me' }] } as any;
    const reply = parent.replies[0];
    component.deleteReply(parent, reply);
    expect(postService.deleteComment).toHaveBeenCalledWith(1, 2);
  });

  it('computes reaction summary', () => {
    const reply = {
      reactions: [{ emoji: '🙂', count: 2 }, { emoji: '👍', count: 1 }]
    } as any;
    expect(component.getReactionSummary(reply)).toContain('3');
  });
});
