import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { Overlay, OverlayModule } from '@angular/cdk/overlay';
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

  it('ignores toggleReplies when id missing', () => {
    component.toggleReplies({ id: null } as any);
    expect(component.isRepliesExpanded({ id: null } as any)).toBeFalse();
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

  it('initializes replies when missing', () => {
    const comment = { id: 1, content: 'hi', username: 'bob', createdAt: new Date().toISOString() } as any;
    component.startReply(comment);
    component.replyForm.setValue({ reply: 'reply' });

    component.addReply(comment);

    expect(comment.replies?.length).toBe(1);
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

  it('skips add comment when form invalid', () => {
    component.commentForm.setValue({ comment: '' });
    component.addComment();
    expect(postService.addComment).not.toHaveBeenCalled();
  });

  it('deletes reply', () => {
    const parent = { id: 1, replies: [{ id: 2, username: 'me' }] } as any;
    const reply = parent.replies[0];
    component.deleteReply(parent, reply);
    expect(postService.deleteComment).toHaveBeenCalledWith(1, 2);
  });

  it('skips delete reply when replies missing', () => {
    const parent = { id: 1, replies: null } as any;
    component.deleteReply(parent, { id: 2, username: 'me' } as any);
    expect(postService.deleteComment).toHaveBeenCalled();
    expect(parent.replies).toBeNull();
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

  it('toggleReactionPicker ignores invalid reply', () => {
    component.toggleReactionPicker({ id: null } as any);
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

  it('skips toggleReplyReaction when missing id or already reacting', () => {
    const reply = { id: null } as any;
    component.toggleReplyReaction(reply, '👍');
    expect(postService.toggleReplyReaction).not.toHaveBeenCalled();

    const reply2 = { id: 8 } as any;
    (component as any).reactingReplyIds.add(8);
    component.toggleReplyReaction(reply2, '👍');
    expect(postService.toggleReplyReaction).not.toHaveBeenCalled();
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

  it('returns reaction count zero when reactions missing', () => {
    expect(component.getReactionCount({} as any, '👍')).toBe(0);
  });

  it('parses mention fragments around caret', () => {
    const result = (component as any).findMentionAtCaret('hello @al', 9);
    expect(result?.query).toBe('al');
  });

  it('findMentionAtCaret returns null without @', () => {
    const result = (component as any).findMentionAtCaret('hello there', 5);
    expect(result).toBeNull();
  });

  it('closeMentionPicker resets state', () => {
    (component as any).mentionOpen = true;
    (component as any).mentionResults = [{ username: 'alice' }];
    (component as any).mentionActiveInput = 'comment';
    (component as any).closeMentionPicker();
    expect((component as any).mentionOpen).toBeFalse();
    expect((component as any).mentionResults.length).toBe(0);
    expect((component as any).mentionActiveInput).toBeNull();
  });

  it('returns empty parts for blank content', () => {
    expect(component.parseContent('')).toEqual([]);
  });

  it('parses multiple mentions', () => {
    const parts = component.parseContent('@alice hi @bob');
    expect(parts.filter(p => p.isMention).length).toBe(2);
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

  it('getReactionSummary trims to top two emojis', () => {
    const reply = {
      reactions: [
        { emoji: '👍', count: 4 },
        { emoji: '🔥', count: 3 },
        { emoji: '😂', count: 1 }
      ]
    } as any;
    expect(component.getReactionSummary(reply)).toContain('👍 🔥');
  });

  it('hasReaction checks viewer selection', () => {
    const reply = { viewerReaction: '👍' } as any;
    expect(component.hasReaction(reply, '👍')).toBeTrue();
    expect(component.hasReaction(reply, '🔥')).toBeFalse();
  });

  it('isFocusedComment/Reply return false for mismatched ids', () => {
    component.focusCommentId = 1;
    component.focusReplyId = 2;
    expect(component.isFocusedComment({ id: 3 } as any)).toBeFalse();
    expect(component.isFocusedReply({ id: 4 } as any)).toBeFalse();
  });

  it('toggles comments visibility', () => {
    component.showComments = false;
    component.toggleComments();
    expect(component.showComments).toBeTrue();
  });

  it('opens comments when auto open changes', () => {
    component.showComments = false;
    component.autoOpenComments = true;
    component.ngOnChanges({
      autoOpenComments: new SimpleChange(false, true, false)
    });
    expect(component.showComments).toBeTrue();
  });

  it('opens comments when focus ids change', () => {
    component.showComments = false;
    component.focusCommentId = 2;
    component.ngOnChanges({
      focusCommentId: new SimpleChange(null, 2, false)
    });
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

  it('hasActions returns false when not owner', () => {
    component.post.username = 'other';
    component.canEdit = true;
    expect(component.hasActions).toBeFalse();
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

  it('selectMention inserts username into input', () => {
    const input = document.createElement('input');
    input.value = 'Hello @al';
    (component as any).mentionInputEl = input;
    (component as any).mentionStartIndex = 6;
    (component as any).mentionCaretIndex = input.value.length;
    (component as any).mentionActiveInput = 'comment';
    component.commentForm.setValue({ comment: input.value });

    component.selectMention({ username: 'alice' } as any);

    expect(input.value).toContain('@alice ');
    expect(component.commentForm.value.comment).toContain('@alice ');
  });

  it('selectMention updates reply input', () => {
    const input = document.createElement('input');
    input.value = '@bo';
    (component as any).mentionInputEl = input;
    (component as any).mentionStartIndex = 0;
    (component as any).mentionCaretIndex = input.value.length;
    (component as any).mentionActiveInput = 'reply';
    component.replyForm.setValue({ reply: input.value });

    component.selectMention({ username: 'bob' } as any);

    expect(component.replyForm.value.reply).toContain('@bob ');
  });

  it('onMentionKeydown navigates list and selects', () => {
    (component as any).mentionOpen = true;
    (component as any).mentionResults = [{ username: 'alice' }, { username: 'bob' }];
    (component as any).mentionIndex = 0;
    const selectSpy = spyOn(component as any, 'selectMention');
    const closeSpy = spyOn(component as any, 'closeMentionPicker');

    component.onMentionKeydown({ key: 'ArrowDown', preventDefault: jasmine.createSpy('pd') } as any);
    expect((component as any).mentionIndex).toBe(1);

    component.onMentionKeydown({ key: 'ArrowUp', preventDefault: jasmine.createSpy('pu') } as any);
    expect((component as any).mentionIndex).toBe(0);

    component.onMentionKeydown({ key: 'Enter', preventDefault: jasmine.createSpy('pe') } as any);
    expect(selectSpy).toHaveBeenCalled();

    component.onMentionKeydown({ key: 'Escape', preventDefault: jasmine.createSpy('px') } as any);
    expect(closeSpy).toHaveBeenCalled();
  });

  it('onMentionKeydown no-ops when closed', () => {
    (component as any).mentionOpen = false;
    component.onMentionKeydown({ key: 'ArrowDown', preventDefault: jasmine.createSpy('pd') } as any);
    expect((component as any).mentionIndex).toBe(0);
  });

  it('onMentionInput closes picker when no mention', () => {
    (component as any).closeMentionPicker = jasmine.createSpy('closeMentionPicker');
    const input = document.createElement('input');
    input.value = 'hello there';
    component.onMentionInput({ target: input } as any, 'comment');
    expect((component as any).closeMentionPicker).toHaveBeenCalled();
  });

  it('onMentionInput no-ops when input missing', () => {
    component.mentionOpen = true;

    component.onMentionInput({ target: null } as any, 'comment');

    expect(component.mentionOpen).toBeTrue();
  });

  it('opens mention picker when suggestions return results', fakeAsync(() => {
    const profileService = TestBed.inject(ProfileService) as any;
    spyOn(profileService, 'getMentionSuggestions').and.returnValue(of([{ username: 'bob' }]));
    const openSpy = spyOn(component as any, 'openMentionOverlay').and.callThrough();

    const input = document.createElement('input');
    input.value = '@b';
    input.selectionStart = 2;

    component.onMentionInput({ target: input } as any, 'comment');
    tick(210);

    expect(component.mentionOpen).toBeTrue();
    expect(openSpy).toHaveBeenCalled();
  }));

  it('closes mention picker when suggestions are empty', fakeAsync(() => {
    const profileService = TestBed.inject(ProfileService) as any;
    spyOn(profileService, 'getMentionSuggestions').and.returnValue(of([]));
    const closeSpy = spyOn(component as any, 'closeMentionOverlay').and.callThrough();

    const input = document.createElement('input');
    input.value = '@a';
    input.selectionStart = 2;

    component.onMentionInput({ target: input } as any, 'comment');
    tick(210);

    expect(component.mentionOpen).toBeFalse();
    expect(closeSpy).toHaveBeenCalled();
  }));

  it('handles mention search errors', fakeAsync(() => {
    const profileService = TestBed.inject(ProfileService) as any;
    spyOn(profileService, 'getMentionSuggestions').and.returnValue(throwError(() => new Error('fail')));

    const input = document.createElement('input');
    input.value = '@a';
    input.selectionStart = 2;

    component.onMentionInput({ target: input } as any, 'comment');
    tick(210);

    expect(component.mentionOpen).toBeFalse();
    expect(component.mentionResults.length).toBe(0);
  }));

  it('onMentionBlur closes picker after delay', fakeAsync(() => {
    (component as any).mentionOpen = true;
    (component as any).mentionResults = [{ username: 'alice' }];
    (component as any).closeMentionPicker = jasmine.createSpy('closeMentionPicker');

    component.onMentionBlur();
    tick(160);
    expect((component as any).closeMentionPicker).toHaveBeenCalled();
  }));

  it('openMentionOverlay updates size and position', () => {
    const input = document.createElement('input');
    input.value = '@a';
    (component as any).mentionInputEl = input;
    (component as any).mentionMenuTemplate = {} as any;
    const updateSizeSpy = jasmine.createSpy('updateSize');
    const updatePositionSpy = jasmine.createSpy('updatePosition');
    const disposeSpy = jasmine.createSpy('dispose');
    (component as any).mentionOriginEl = input;
    (component as any).mentionOverlayRef = {
      hasAttached: () => true,
      updateSize: updateSizeSpy,
      updatePosition: updatePositionSpy,
      dispose: disposeSpy
    } as any;

    (component as any).openMentionOverlay();
    expect(updateSizeSpy).toHaveBeenCalled();
    expect(updatePositionSpy).toHaveBeenCalled();
  });

  it('openMentionOverlay creates and attaches overlay when needed', () => {
    const overlay = TestBed.inject(Overlay);
    const attachSpy = jasmine.createSpy('attach');
    const updateSizeSpy = jasmine.createSpy('updateSize');
    const updatePositionSpy = jasmine.createSpy('updatePosition');
    const createSpy = spyOn(overlay, 'create').and.returnValue({
      hasAttached: () => false,
      attach: attachSpy,
      updateSize: updateSizeSpy,
      updatePosition: updatePositionSpy,
      dispose: jasmine.createSpy('dispose')
    } as any);

    const input = document.createElement('input');
    input.value = '@a';
    (component as any).mentionInputEl = input;
    (component as any).mentionMenuTemplate = component.mentionMenuTemplate;
    (component as any).mentionOverlayRef = null;
    (component as any).mentionOriginEl = null;

    (component as any).openMentionOverlay();

    expect(createSpy).toHaveBeenCalled();
    expect(attachSpy).toHaveBeenCalled();
    expect(updateSizeSpy).toHaveBeenCalled();
    expect(updatePositionSpy).toHaveBeenCalled();
  });

  it('openMentionOverlay returns early when input missing', () => {
    (component as any).mentionInputEl = null;
    (component as any).openMentionOverlay();
    expect((component as any).mentionOverlayRef).toBeNull();
  });

  it('closeMentionOverlay detaches when attached', () => {
    const detachSpy = jasmine.createSpy('detach');
    (component as any).mentionOverlayRef = {
      hasAttached: () => true,
      detach: detachSpy,
      dispose: jasmine.createSpy('dispose')
    } as any;

    (component as any).closeMentionOverlay();
    expect(detachSpy).toHaveBeenCalled();
  });

  it('closeMentionOverlay disposes when requested', () => {
    const disposeSpy = jasmine.createSpy('dispose');
    (component as any).mentionOverlayRef = { dispose: disposeSpy, hasAttached: () => false } as any;
    (component as any).mentionOriginEl = document.createElement('input');

    (component as any).closeMentionOverlay(true);
    expect(disposeSpy).toHaveBeenCalled();
    expect((component as any).mentionOverlayRef).toBeNull();
  });

  it('closeReactionPicker clears active id', () => {
    component.activeReactionPickerId = 5;
    component.closeReactionPicker();
    expect(component.activeReactionPickerId).toBeNull();
  });

  it('isMentionActive returns true for active input', () => {
    (component as any).mentionOpen = true;
    (component as any).mentionActiveInput = 'comment';
    (component as any).mentionResults = [{ username: 'alice' }];
    expect(component.isMentionActive('comment')).toBeTrue();
  });

  it('refreshPostComments handles error', () => {
    postService.getPostById.and.returnValue(throwError(() => new Error('fail')));
    (component as any).refreshPostComments();
    expect(postService.getPostById).toHaveBeenCalled();
  });

  it('adds reply skips when not authenticated', () => {
    component.currentUsername = null;
    const comment = { id: 1, replies: [] } as any;
    component.replyForm.setValue({ reply: 'reply' });
    component.addReply(comment);
    expect(postService.addComment).not.toHaveBeenCalled();
  });

  it('parses content without mentions', () => {
    const parts = component.parseContent('hello world');
    expect(parts.length).toBe(1);
    expect(parts[0].isMention).toBeFalse();
  });

  it('onMentionInput uses search for longer queries', fakeAsync(() => {
    const profileService = TestBed.inject(ProfileService) as any;
    const searchSpy = spyOn(profileService, 'searchUsers').and.returnValue(of([{ username: 'alice' }]));
    const input = document.createElement('input');
    input.value = '@al';
    input.selectionStart = 3;

    component.onMentionInput({ target: input } as any, 'comment');
    tick(210);

    expect(searchSpy).toHaveBeenCalledWith('al', 6);
  }));

  it('onMentionInput sets active input and indices for mention', () => {
    const input = document.createElement('input');
    input.value = '@al';
    input.selectionStart = 3;

    component.onMentionInput({ target: input } as any, 'comment');

    expect((component as any).mentionActiveInput).toBe('comment');
    expect((component as any).mentionStartIndex).toBe(0);
    expect((component as any).mentionCaretIndex).toBe(3);
    expect((component as any).mentionInputEl).toBe(input);
  });

  it('onMentionKeydown ignores enter with no selection', () => {
    (component as any).mentionOpen = true;
    (component as any).mentionResults = [];
    const selectSpy = spyOn(component as any, 'selectMention');

    component.onMentionKeydown({ key: 'Enter', preventDefault: jasmine.createSpy('pd') } as any);

    expect(selectSpy).not.toHaveBeenCalled();
  });

  it('selectMention returns early when input metadata missing', () => {
    (component as any).mentionInputEl = null;
    (component as any).mentionStartIndex = null;
    (component as any).mentionCaretIndex = null;
    component.selectMention({ username: 'alice' } as any);
    expect(component.commentForm.value.comment).toBe('');
  });

  it('closeMentionPicker closes overlay', () => {
    const closeOverlaySpy = spyOn(component as any, 'closeMentionOverlay');
    (component as any).closeMentionPicker();
    expect(closeOverlaySpy).toHaveBeenCalled();
  });

  it('confirmDelete no-ops when saving', () => {
    component.isSaving = true;
    component.canDelete = true;
    component.post.username = 'me';
    component.confirmDelete();
    expect(postService.deletePost).not.toHaveBeenCalled();
  });

  it('confirmDelete no-ops when not allowed', () => {
    component.isSaving = false;
    component.canDelete = false;
    component.post.username = 'me';
    component.confirmDelete();
    expect(postService.deletePost).not.toHaveBeenCalled();
  });

  it('logs error when toggling like fails', () => {
    const errorSpy = spyOn(console, 'error');
    postService.toggleLike.and.returnValue(throwError(() => new Error('fail')));
    component.toggleLike();
    expect(errorSpy).toHaveBeenCalled();
  });

  it('logs error when adding comment fails', () => {
    const errorSpy = spyOn(console, 'error');
    postService.addComment.and.returnValue(throwError(() => new Error('fail')));
    component.commentForm.setValue({ comment: 'hello' });
    component.addComment();
    expect(errorSpy).toHaveBeenCalled();
  });

  it('clears reacting flag on reaction error', () => {
    const reply = { id: 6, reactions: [] } as any;
    postService.toggleReplyReaction.and.returnValue(throwError(() => new Error('fail')));
    component.toggleReplyReaction(reply, 'ok');
    expect((component as any).reactingReplyIds.has(6)).toBeFalse();
  });

  it('skips toggleReplyReaction when not authenticated', () => {
    component.currentUsername = null;
    component.toggleReplyReaction({ id: 3 } as any, 'ok');
    expect(postService.toggleReplyReaction).not.toHaveBeenCalled();
  });

  it('opens delete confirm when allowed', () => {
    component.canDelete = true;
    component.post.username = 'me';
    component.openDeleteConfirm();
    expect(postDialogService.openDelete).toHaveBeenCalledWith(1);
  });

  it('handles delete errors', () => {
    const errorSpy = spyOn(console, 'error');
    component.canDelete = true;
    component.post.username = 'me';
    postService.deletePost.and.returnValue(throwError(() => new Error('fail')));

    component.confirmDelete();

    expect(errorSpy).toHaveBeenCalled();
    expect(component.errorMessage).toContain('Failed to delete post');
    postService.deletePost.and.returnValue(of(void 0));
  });

  it('onMentionInput clears close timer', () => {
    const clearSpy = spyOn(window, 'clearTimeout');
    (component as any).mentionCloseTimer = window.setTimeout(() => {}, 10);
    const input = document.createElement('input');
    input.value = '@a';
    input.selectionStart = 2;

    component.onMentionInput({ target: input } as any, 'comment');

    expect(clearSpy).toHaveBeenCalled();
  });

  it('findMentionAtCaret returns null for empty value', () => {
    const result = (component as any).findMentionAtCaret('', 0);
    expect(result).toBeNull();
  });

});
