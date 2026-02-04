import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
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
  let postDialogService: { activeDeletePostId$: any; openDelete: jasmine.Spy; closeDelete: jasmine.Spy };

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
    postDialogService = {
      activeDeletePostId$: of(null),
      openDelete: jasmine.createSpy('openDelete'),
      closeDelete: jasmine.createSpy('closeDelete')
    };
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
    postService.updatePost.and.returnValue(of({
      id: 1,
      imageUrl: 'image',
      caption: 'caption',
      username: 'owner',
      createdAt: new Date().toISOString(),
      likesCount: 0,
      likedByCurrentUser: false,
      comments: []
    }));
    postService.deletePost.and.returnValue(of(void 0));
    await TestBed.configureTestingModule({
      imports: [PostCardComponent, HttpClientTestingModule, RouterTestingModule, OverlayModule],
      providers: [
        { provide: PostService, useValue: postService },
        { provide: AuthService, useValue: { getUsername: () => 'me' } },
        { provide: PostDialogService, useValue: postDialogService },
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
    component.toggleReplies(comment);
    expect(component.isRepliesExpanded(comment)).toBeFalse();
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
  it('handles reply failure', () => {
    const comment = { id: 1, content: 'hi', username: 'bob', createdAt: new Date().toISOString(), replies: [] } as any;
    postService.addComment.and.returnValue(throwError(() => new Error('fail')));
    component.startReply(comment);
    component.replyForm.setValue({ reply: 'reply' });
    component.addReply(comment);
    expect(component.errorMessage).toContain('Failed to add reply');
  });

  it('ignores like when not authenticated', () => {
    component.currentUsername = null;
    component.toggleLike();
    expect(postService.toggleLike).not.toHaveBeenCalled();
  });

  it('toggleReactionPicker sets active id', () => {
    const reply = { id: 7 } as any;
    component.toggleReactionPicker(reply);
    expect(component.activeReactionPickerId).toBe(7);
    component.toggleReactionPicker(reply);
    expect(component.activeReactionPickerId).toBeNull();
  });

  it('toggleReplyReaction updates summary', () => {
    const reply = { id: 5, reactions: [] } as any;
    postService.toggleReplyReaction.and.returnValue(of({
      commentId: 5,
      reactions: [{ emoji: '👍', count: 2 }],
      viewerReaction: '👍'
    } as any));
    component.toggleReplyReaction(reply, '👍');
    expect(reply.viewerReaction).toBe('👍');
    expect(reply.reactions?.length).toBe(1);
  });

  it('skips deleting reply when not owner', () => {
    const parent = { id: 1, replies: [{ id: 2, username: 'bob' }] } as any;
    const reply = parent.replies[0];
    component.deleteReply(parent, reply);
    expect(postService.deleteComment).not.toHaveBeenCalled();
  });

  it('starts and cancels edit', () => {
    component.canEdit = true;
    component.post.username = 'me';
    component.startEdit();
    expect(component.isEditing).toBeTrue();
    component.cancelEdit();
    expect(component.isEditing).toBeFalse();
  });

  it('saves edits and handles error', () => {
    component.canEdit = true;
    component.post.username = 'me';
    component.startEdit();
    component.editForm.setValue({ caption: 'new caption' });
    component.saveEdit();
    expect(postService.updatePost).toHaveBeenCalled();

    postService.updatePost.and.returnValue(throwError(() => new Error('fail')));
    component.startEdit();
    component.editForm.setValue({ caption: 'oops' });
    component.saveEdit();
    expect(component.errorMessage).toContain('Failed to update post');
    postService.updatePost.and.returnValue(of({
      id: 1,
      imageUrl: 'image',
      caption: 'caption',
      username: 'owner',
      createdAt: new Date().toISOString(),
      likesCount: 0,
      likedByCurrentUser: false,
      comments: []
    }));
  });

  it('opens and confirms delete', () => {
    component.canDelete = true;
    component.post.username = 'me';
    const postDeletedSpy = jasmine.createSpy('postDeleted');
    component.postDeleted.subscribe(postDeletedSpy);

    component.openDeleteConfirm();
    component.confirmDelete();

    expect(postService.deletePost).toHaveBeenCalled();
    expect(postDeletedSpy).toHaveBeenCalledWith(1);
  });

  it('returns reaction counts for emoji', () => {
    const reply = { reactions: [{ emoji: '👍', count: 2 }] } as any;
    expect(component.getReactionCount(reply, '👍')).toBe(2);
    expect(component.getReactionCount(reply, '🔥')).toBe(0);
  });

  it('parses mention fragments around caret', () => {
    const result = (component as any).findMentionAtCaret('hello @al', 9);
    expect(result?.query).toBe('al');
  });

  it('returns empty parts for blank content', () => {
    expect(component.parseContent('')).toEqual([]);
  });

  it('skips add comment when not authenticated', () => {
    component.currentUsername = null;
    component.commentForm.setValue({ comment: 'hello' });
    component.addComment();
    expect(postService.addComment).not.toHaveBeenCalled();
  });

  it('ignores startReply without current user', () => {
    component.currentUsername = null;
    component.startReply({ id: 1 } as any);
    expect(component.activeReplyCommentId).toBeNull();
  });

  it('skips addReply when form invalid', () => {
    const comment = { id: 1, content: 'hi', username: 'bob', createdAt: new Date().toISOString(), replies: [] } as any;
    component.startReply(comment);
    component.replyForm.setValue({ reply: '' });
    component.addReply(comment);
    expect(postService.addComment).not.toHaveBeenCalled();
  });

  it('skips delete when no current user', () => {
    component.currentUsername = null;
    const parent = { id: 1, replies: [{ id: 2, username: 'me' }] } as any;
    component.deleteReply(parent, parent.replies[0]);
    expect(postService.deleteComment).not.toHaveBeenCalled();
  });

  it('returns empty summary when no reactions', () => {
    expect(component.getReactionSummary({} as any)).toBe('');
  });

  it('hasReaction checks viewer selection', () => {
    const reply = { viewerReaction: '👍' } as any;
    expect(component.hasReaction(reply, '👍')).toBeTrue();
    expect(component.hasReaction(reply, '🔥')).toBeFalse();
  });

  it('toggles comments visibility', () => {
    component.showComments = false;
    component.toggleComments();
    expect(component.showComments).toBeTrue();
  });

  it('returns focus flags', () => {
    component.focusCommentId = 5;
    component.focusReplyId = 7;
    expect(component.isFocusedComment({ id: 5 } as any)).toBeTrue();
    expect(component.isFocusedReply({ id: 7 } as any)).toBeTrue();
  });

  it('computes ownership and actions flags', () => {
    component.post.username = 'me';
    component.canEdit = true;
    component.canDelete = false;
    expect(component.isOwner).toBeTrue();
    expect(component.hasActions).toBeTrue();
  });

  it('toggleActions respects permissions', () => {
    component.post.username = 'other';
    component.canEdit = false;
    component.canDelete = false;
    component.toggleActions();
    expect(component.showActions).toBeFalse();

    component.post.username = 'me';
    component.canEdit = true;
    component.toggleActions();
    expect(component.showActions).toBeTrue();
  });

  it('does not start edit when not owner', () => {
    component.canEdit = true;
    component.post.username = 'other';
    component.startEdit();
    expect(component.isEditing).toBeFalse();
  });

  it('skips save when already saving', () => {
    component.canEdit = true;
    component.post.username = 'me';
    component.isSaving = true;
    component.saveEdit();
    expect(postService.updatePost).not.toHaveBeenCalled();
  });

  it('does not save when not owner', () => {
    component.canEdit = true;
    component.post.username = 'other';
    component.saveEdit();
    expect(postService.updatePost).not.toHaveBeenCalled();
  });

  it('does not open delete when not allowed', () => {
    component.canDelete = false;
    component.post.username = 'me';
    component.openDeleteConfirm();
    expect(postDialogService.openDelete).not.toHaveBeenCalled();
  });

  it('closeDeleteConfirm respects saving flag', () => {
    component.isSaving = true;
    component.closeDeleteConfirm();
    expect(postDialogService.closeDelete).not.toHaveBeenCalled();
  });

  it('closeDeleteConfirm calls service when allowed', () => {
    component.isSaving = false;
    component.closeDeleteConfirm();
    expect(postDialogService.closeDelete).toHaveBeenCalled();
  });
});
