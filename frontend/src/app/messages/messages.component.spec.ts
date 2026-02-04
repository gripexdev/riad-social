import { TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { ElementRef } from '@angular/core';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { HttpEventType } from '@angular/common/http';
import { MessagesComponent } from './messages.component';
import { MessageService } from './message.service';
import { MessageRealtimeService } from './message-realtime.service';
import { AuthService } from '../auth/auth.service';

const nowIso = new Date().toISOString();

describe('MessagesComponent', () => {
  let messageServiceSpy: jasmine.SpyObj<MessageService>;
  let realtimeServiceSpy: jasmine.SpyObj<MessageRealtimeService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let queryParamMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(async () => {
    messageServiceSpy = jasmine.createSpyObj<MessageService>('MessageService', [
      'getConversations',
      'getMessages',
      'markConversationRead',
      'sendMessage',
      'createAttachmentUploadSessions',
      'uploadAttachmentChunk',
      'finalizeAttachmentUpload',
      'cancelAttachmentUpload'
    ]);
    realtimeServiceSpy = jasmine.createSpyObj<MessageRealtimeService>('MessageRealtimeService', [
      'connect',
      'disconnect',
      'onMessage',
      'onTyping',
      'sendTyping'
    ]);
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['getUsername', 'isAuthenticated']);

    paramMap$ = new BehaviorSubject(convertToParamMap({}));
    queryParamMap$ = new BehaviorSubject(convertToParamMap({}));

    messageServiceSpy.getConversations.and.returnValue(of([]));
    messageServiceSpy.getMessages.and.returnValue(of([]));
    messageServiceSpy.markConversationRead.and.returnValue(of(void 0));
    messageServiceSpy.sendMessage.and.returnValue(of({
      id: 1,
      conversationId: 10,
      senderUsername: 'alice',
      recipientUsername: 'bob',
      content: 'hi',
      createdAt: nowIso,
      isRead: false
    } as any));
    messageServiceSpy.createAttachmentUploadSessions.and.returnValue(of({ message: {
      id: 2,
      conversationId: 11,
      senderUsername: 'alice',
      recipientUsername: 'bob',
      content: 'hello',
      attachments: [],
      createdAt: nowIso,
      isRead: false
    }, uploads: [] } as any));
    messageServiceSpy.uploadAttachmentChunk.and.returnValue(of({ type: HttpEventType.UploadProgress, loaded: 1 } as any));
    messageServiceSpy.finalizeAttachmentUpload.and.returnValue(of({} as any));
    messageServiceSpy.cancelAttachmentUpload.and.returnValue(of(void 0));

    realtimeServiceSpy.onMessage.and.returnValue(new Subject());
    realtimeServiceSpy.onTyping.and.returnValue(new Subject());
    authServiceSpy.getUsername.and.returnValue('alice');
    authServiceSpy.isAuthenticated.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [MessagesComponent, RouterTestingModule],
      providers: [
        { provide: MessageService, useValue: messageServiceSpy },
        { provide: MessageRealtimeService, useValue: realtimeServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: paramMap$.asObservable(),
            queryParamMap: queryParamMap$.asObservable()
          }
        }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  });

  function createComponent() {
    const fixture = TestBed.createComponent(MessagesComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    return { fixture, component };
  }

  it('starts a new message and resets state', () => {
    const { component } = createComponent();
    component.selectedConversationId = 12;
    component.messages = [{ id: 1 } as any];
    component.attachments$.next([{ id: 'a1', file: new File(['x'], 'x.txt'), type: 'DOCUMENT', displayName: 'x', sizeBytes: 1, altText: '', wasCompressed: false, status: 'DRAFT', progress: 0 } as any]);

    component.startNewMessage();

    expect(component.selectedConversationId).toBeNull();
    expect(component.isComposingNew).toBeTrue();
    expect(component.recipientControl.value).toBe('');
  });

  it('navigates to an existing conversation when a recipient is provided', () => {
    const { component } = createComponent();
    component.conversations = [{ id: 22, participantUsername: 'bob', unreadCount: 0 } as any];

    queryParamMap$.next(convertToParamMap({ recipient: 'bob' }));

    expect(router.navigate).toHaveBeenCalledWith(['/messages', 22]);
  });

  it('prepares composer when recipient has no conversation', () => {
    const { component } = createComponent();

    queryParamMap$.next(convertToParamMap({ recipient: 'carol' }));

    expect(component.isComposingNew).toBeTrue();
    expect(component.recipientControl.value).toBe('carol');
  });

  it('resolves attachment types and sizes', () => {
    const { component } = createComponent();
    const imageFile = new File(['a'], 'photo.jpg', { type: 'image/jpeg' });
    const videoFile = new File(['a'], 'clip.mp4', { type: 'video/mp4' });
    const docFile = new File(['a'], 'doc.pdf', { type: 'application/pdf' });

    expect((component as any).resolveAttachmentType(imageFile)).toBe('IMAGE');
    expect((component as any).resolveAttachmentType(videoFile)).toBe('VIDEO');
    expect((component as any).resolveAttachmentType(docFile)).toBe('DOCUMENT');
    expect((component as any).maxBytesForType('IMAGE')).toBe(component.maxImageBytes);
  });

  it('builds previews for messages and attachments', () => {
    const { component } = createComponent();
    const preview = (component as any).buildPreview('Hello there', []);
    const attachmentPreview = (component as any).buildPreview('', [{ type: 'IMAGE' } as any]);
    const pluralPreview = (component as any).buildPreview('', [{ type: 'IMAGE' } as any, { type: 'VIDEO' } as any]);

    expect(preview).toBe('Hello there');
    expect(attachmentPreview).toBe('Photo');
    expect(pluralPreview).toBe('2 attachments');
  });

  it('sanitizes alt text updates', () => {
    const { component } = createComponent();
    const item = { id: 'a1', file: new File(['a'], 'photo.jpg', { type: 'image/jpeg' }), type: 'IMAGE', displayName: 'photo.jpg', sizeBytes: 10, altText: '', wasCompressed: false, status: 'DRAFT', progress: 0 } as any;
    component.attachments$.next([item]);

    component.updateAltText('a1', { target: { value: '<b>hello</b>' } } as any);

    expect(component.attachmentItems[0].altText).toBe('bhello/b');
  });

  it('shows send error when recipient is missing', async () => {
    const { component } = createComponent();
    component.recipientControl.setValue('');
    component.messageControl.setValue('hi');

    await component.sendMessage();

    expect(component.sendError).toBe('Recipient username is required.');
  });

  it('starts attachment upload flow when sending with attachments', async () => {
    const { component } = createComponent();
    const file = new File(['hello'], 'note.txt', { type: 'text/plain' });
    component.attachments$.next([{
      id: 'a1',
      file,
      type: 'DOCUMENT',
      displayName: 'note.txt',
      sizeBytes: file.size,
      altText: '',
      wasCompressed: false,
      status: 'DRAFT',
      progress: 0
    } as any]);
    component.recipientControl.setValue('bob');
    spyOn(component as any, 'startUpload').and.returnValue(Promise.resolve());
    messageServiceSpy.createAttachmentUploadSessions.and.returnValue(of({
      message: {
        id: 3,
        conversationId: 33,
        senderUsername: 'alice',
        recipientUsername: 'bob',
        content: 'hello',
        attachments: [{ id: 10, status: 'UPLOADING' }],
        createdAt: nowIso,
        isRead: false
      },
      uploads: [{ uploadId: 'u1', attachmentId: 10, uploadUrl: 'u', finalizeUrl: 'f', chunkSizeBytes: 1024 }]
    } as any));

    await component.sendMessage();

    expect((component as any).startUpload).toHaveBeenCalled();
    expect(component.attachmentItems[0].status).toBe('UPLOADING');
  });

  it('uploads a single chunk and finalizes', fakeAsync(() => {
    const { component } = createComponent();
    const file = new File(['hello'], 'note.txt', { type: 'text/plain' });
    const itemId = 'a1';
    component.attachments$.next([{
      id: itemId,
      file,
      type: 'DOCUMENT',
      displayName: 'note.txt',
      sizeBytes: file.size,
      altText: '',
      wasCompressed: false,
      status: 'UPLOADING',
      progress: 0,
      uploadId: 'upload1',
      attachmentId: 10,
      chunkSizeBytes: 1024
    } as any]);

    messageServiceSpy.uploadAttachmentChunk.and.returnValue(of(
      { type: HttpEventType.UploadProgress, loaded: file.size } as any,
      { type: HttpEventType.Response, body: { uploadedChunks: 1 } } as any
    ));

    (component as any).startUpload(itemId);
    flushMicrotasks();
    tick(500);

    expect(component.attachmentItems.length).toBe(0);
  }));

  it('formats times and attachment labels', () => {
    const { component } = createComponent();
    const now = new Date().toISOString();
    const twoHoursAgo = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();
    const twoDaysAgo = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString();

    expect(component.formatRelativeTime(now)).toBe('now');
    expect(component.formatRelativeTime(twoHoursAgo)).toBe('2h');
    expect(component.formatRelativeTime(twoDaysAgo)).toBe('2d');
    expect(component.getAttachmentLabel({ status: 'EXPIRED' } as any)).toBe('Expired attachment');
    expect(component.getAttachmentLabel({ status: 'FAILED' } as any)).toBe('Attachment failed');
    expect(component.getAttachmentLabel({ status: 'QUARANTINED' } as any)).toBe('Attachment quarantined');
    expect(component.getAttachmentLabel({ status: 'UPLOADING' } as any)).toBe('Uploading');
    expect(component.getAttachmentStatusClass({ status: 'READY' } as any)).toBe('status-ready');
  });

  it('builds conversation previews with sender prefix', () => {
    const { component } = createComponent();
    component.conversations = [{
      id: 1,
      participantUsername: 'bob',
      lastMessagePreview: 'hello',
      lastMessageSenderUsername: 'alice'
    } as any];
    component.selectedConversationId = 1;

    expect(component.getConversationPreview(component.conversations[0])).toBe('You: hello');
  });

  it('returns default preview when conversation empty', () => {
    const { component } = createComponent();
    const preview = component.getConversationPreview({} as any);
    expect(preview).toContain('Say hello');
  });

  it('builds attachment previews by type', () => {
    const { component } = createComponent();
    const photo = (component as any).buildPreview('', [{ type: 'IMAGE' } as any]);
    const video = (component as any).buildPreview('', [{ type: 'VIDEO' } as any]);
    const doc = (component as any).buildPreview('', [{ type: 'DOCUMENT' } as any]);
    const many = (component as any).buildPreview('', [{ type: 'IMAGE' } as any, { type: 'VIDEO' } as any]);

    expect(photo).toBe('Photo');
    expect(video).toBe('Video');
    expect(doc).toBe('Document');
    expect(many).toBe('2 attachments');
  });

  it('trims long previews', () => {
    const { component } = createComponent();
    const long = 'a'.repeat(component.previewLimit + 10);
    const preview = (component as any).buildPreview(long, []);
    expect(preview.endsWith('...')).toBeTrue();
  });

  it('resolves attachment types by extension', () => {
    const { component } = createComponent();
    const jpg = new File(['x'], 'photo.jpg', { type: '' });
    const mp4 = new File(['x'], 'clip.mp4', { type: '' });
    const pdf = new File(['x'], 'doc.pdf', { type: '' });

    expect((component as any).resolveAttachmentType(jpg)).toBe('IMAGE');
    expect((component as any).resolveAttachmentType(mp4)).toBe('VIDEO');
    expect((component as any).resolveAttachmentType(pdf)).toBe('DOCUMENT');
  });

  it('returns mime types for attachment kinds', () => {
    const { component } = createComponent();
    expect((component as any).mimeFromType('IMAGE')).toBe('image/jpeg');
    expect((component as any).mimeFromType('VIDEO')).toBe('video/mp4');
    expect((component as any).mimeFromType('DOCUMENT')).toBe('application/pdf');
  });

  it('trackBy helpers return ids', () => {
    const { component } = createComponent();
    expect(component.trackByConversationId(0, { id: 5 } as any)).toBe(5);
    expect(component.trackByMessageId(0, { id: 7 } as any)).toBe(7);
    expect(component.trackByAttachmentId(0, { id: 9 } as any)).toBe(9);
    expect(component.trackByAttachmentItemId(0, { id: 'a1' } as any)).toBe('a1');
  });

  it('returns max bytes for each type', () => {
    const { component } = createComponent();
    expect((component as any).maxBytesForType('IMAGE')).toBe(component.maxImageBytes);
    expect((component as any).maxBytesForType('VIDEO')).toBe(component.maxVideoBytes);
    expect((component as any).maxBytesForType('DOCUMENT')).toBe(component.maxDocumentBytes);
  });

  it('returns null for unsupported attachment types', () => {
    const { component } = createComponent();
    const file = new File(['x'], 'archive.zip', { type: 'application/zip' });
    expect((component as any).resolveAttachmentType(file)).toBeNull();
  });

  it('buildUploadRequests sets altText only for images', () => {
    const { component } = createComponent();
    const items = [
      {
        id: 'a1',
        file: new File(['x'], 'photo.jpg', { type: 'image/jpeg' }),
        type: 'IMAGE',
        displayName: 'photo.jpg',
        sizeBytes: 1,
        altText: 'alt',
        wasCompressed: false,
        status: 'DRAFT',
        progress: 0
      },
      {
        id: 'a2',
        file: new File(['x'], 'doc.pdf', { type: 'application/pdf' }),
        type: 'DOCUMENT',
        displayName: 'doc.pdf',
        sizeBytes: 1,
        altText: 'ignored',
        wasCompressed: false,
        status: 'DRAFT',
        progress: 0
      }
    ] as any;

    const requests = (component as any).buildUploadRequests(items);
    expect(requests[0].altText).toBe('alt');
    expect(requests[1].altText).toBeNull();
  });

  it('updates alt text with length limit', () => {
    const { component } = createComponent();
    const item = { id: 'a1', file: new File(['a'], 'photo.jpg', { type: 'image/jpeg' }), type: 'IMAGE', displayName: 'photo.jpg', sizeBytes: 10, altText: '', wasCompressed: false, status: 'DRAFT', progress: 0 } as any;
    component.attachments$.next([item]);

    const long = '<' + 'a'.repeat(component.maxAltTextLength + 10) + '>';
    component.updateAltText('a1', { target: { value: long } } as any);

    expect(component.attachmentItems[0].altText.length).toBe(component.maxAltTextLength);
  });

  it('applies recipient navigation when conversation exists', () => {
    const { component } = createComponent();
    component.conversations = [{ id: 9, participantUsername: 'bob' } as any];
    (component as any).pendingRecipientUsername = 'bob';
    component.selectedConversationId = null;
    spyOn(component as any, 'hasDraft').and.returnValue(false);

    (component as any).applyRecipientNavigation();

    expect(router.navigate).toHaveBeenCalledWith(['/messages', 9]);
  });

  it('does not apply recipient navigation when draft exists', () => {
    const { component } = createComponent();
    component.conversations = [{ id: 9, participantUsername: 'bob' } as any];
    (component as any).pendingRecipientUsername = 'bob';
    component.messageControl.setValue('draft');

    (component as any).applyRecipientNavigation();

    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('applyRecipientNavigation prepares composer when draft exists', () => {
    const { component } = createComponent();
    component.conversations = [{ id: 9, participantUsername: 'bob' } as any];
    (component as any).pendingRecipientUsername = 'bob';
    component.messageControl.setValue('draft');
    const prepareSpy = spyOn(component as any, 'prepareComposer').and.callThrough();

    (component as any).applyRecipientNavigation();

    expect(prepareSpy).toHaveBeenCalledWith('bob');
    expect(component.isComposingNew).toBeTrue();
  });

  it('applyRecipientNavigation skips when conversation already selected', () => {
    const { component } = createComponent();
    (component as any).pendingRecipientUsername = 'bob';
    component.selectedConversationId = 1;

    (component as any).applyRecipientNavigation();

    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('prepares composer for new recipient', () => {
    const { component } = createComponent();
    (component as any).prepareComposer('carol');
    expect(component.isComposingNew).toBeTrue();
    expect(component.recipientControl.value).toBe('carol');
  });

  it('prepareComposer ignores blank recipient', () => {
    const { component } = createComponent();
    (component as any).prepareComposer('   ');
    expect(component.isComposingNew).toBeFalse();
  });

  it('finds conversations by username', () => {
    const { component } = createComponent();
    component.conversations = [{ id: 1, participantUsername: 'Bob' } as any];
    const found = (component as any).findConversationByUsername('bob');
    expect(found?.id).toBe(1);
  });

  it('findConversationByUsername returns undefined for blank values', () => {
    const { component } = createComponent();
    const found = (component as any).findConversationByUsername('  ');
    expect(found).toBeUndefined();
  });

  it('marks conversations read updates counts', () => {
    const { component } = createComponent();
    component.conversations = [{ id: 1, participantUsername: 'bob', unreadCount: 3 } as any];
    component.selectedConversation = component.conversations[0];
    messageServiceSpy.markConversationRead.and.returnValue(of(void 0));

    (component as any).markConversationRead(1);

    expect(component.conversations[0].unreadCount).toBe(0);
  });

  it('stopTypingSignal clears timeout and sends typing false', () => {
    const { component } = createComponent();
    const clearSpy = spyOn(window, 'clearTimeout');
    component.selectedConversationId = 5;
    (component as any).typingActive = true;
    (component as any).typingSendTimeoutId = window.setTimeout(() => {}, 10);

    (component as any).stopTypingSignal();

    expect(clearSpy).toHaveBeenCalled();
    expect(realtimeServiceSpy.sendTyping).toHaveBeenCalledWith(5, false);
  });

  it('clearTypingIndicator resets typing state', () => {
    const { component } = createComponent();
    const clearSpy = spyOn(window, 'clearTimeout');
    component.typingConversationId = 7;
    component.typingUsername = 'bob';
    (component as any).typingTimeoutId = window.setTimeout(() => {}, 10);

    (component as any).clearTypingIndicator();

    expect(clearSpy).toHaveBeenCalled();
    expect(component.typingConversationId).toBeNull();
    expect(component.typingUsername).toBeNull();
  });

  it('stops typing on blur', () => {
    const { component } = createComponent();
    const spy = spyOn(component as any, 'stopTypingSignal');
    component.onMessageBlur();
    expect(spy).toHaveBeenCalled();
  });

  it('canSend enforces recipient and content rules', () => {
    const { component } = createComponent();
    component.recipientControl.setValue('');
    component.messageControl.setValue('hi');
    expect(component.canSend()).toBeFalse();

    component.recipientControl.setValue('bob');
    component.messageControl.setValue('');
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'DRAFT',
      progress: 0
    } as any]);
    expect(component.canSend()).toBeTrue();
  });

  it('canSend returns false when sending', () => {
    const { component } = createComponent();
    component.isSending = true;
    component.recipientControl.setValue('bob');
    component.messageControl.setValue('hi');
    expect(component.canSend()).toBeFalse();
  });

  it('removes attachments and revokes preview URLs', () => {
    const { component } = createComponent();
    const revokeSpy = spyOn(URL, 'revokeObjectURL');
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'DRAFT',
      progress: 0,
      previewUrl: 'blob:preview'
    } as any]);

    component.removeAttachment('a1');

    expect(revokeSpy).toHaveBeenCalledWith('blob:preview');
    expect(component.attachmentItems.length).toBe(0);
  });

  it('cancels an upload and removes the draft', () => {
    const { component } = createComponent();
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'UPLOADING',
      progress: 20,
      uploadId: 'upload1'
    } as any]);

    component.cancelUpload('a1');

    expect(messageServiceSpy.cancelAttachmentUpload).toHaveBeenCalledWith('upload1');
    expect(component.attachmentItems.length).toBe(0);
  });

  it('retries failed uploads', () => {
    const { component } = createComponent();
    const startSpy = spyOn(component as any, 'startUpload').and.returnValue(Promise.resolve());
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'FAILED',
      progress: 0,
      uploadId: 'upload1',
      attachmentId: 3
    } as any]);

    component.retryUpload('a1');

    expect(startSpy).toHaveBeenCalledWith('a1');
    expect(component.attachmentItems[0].status).toBe('UPLOADING');
  });

  it('builds upload requests with alt text for images', () => {
    const { component } = createComponent();
    const requests = (component as any).buildUploadRequests([{
      id: 'a1',
      file: new File(['x'], 'photo.jpg', { type: 'image/jpeg' }),
      type: 'IMAGE',
      displayName: 'photo.jpg',
      sizeBytes: 10,
      altText: 'alt',
      wasCompressed: false,
      status: 'DRAFT',
      progress: 0
    } as any]);

    expect(requests[0].altText).toBe('alt');
  });

  it('creates attachment ids with crypto when available', () => {
    const { component } = createComponent();
    const cryptoApi = window.crypto as any;
    if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
      const randomSpy = spyOn(cryptoApi, 'randomUUID').and.returnValue('00000000-0000-0000-0000-000000000000');
      const id = (component as any).createAttachmentId();
      expect(randomSpy).toHaveBeenCalled();
      expect(id).toBe('00000000-0000-0000-0000-000000000000');
    }
  });

  it('compressImageFile returns original for gifs', async () => {
    const { component } = createComponent();
    const file = new File(['gif'], 'anim.gif', { type: 'image/gif' });
    const result = await (component as any).compressImageFile(file);
    expect(result.file).toBe(file);
    expect(result.wasCompressed).toBeFalse();
  });

  it('opens and closes media viewer', () => {
    const { component } = createComponent();
    component.openMediaViewer({ id: 1, type: 'IMAGE', status: 'READY', url: 'u' } as any);
    expect(component.mediaViewer).not.toBeNull();
    component.closeMediaViewer();
    expect(component.mediaViewer).toBeNull();
  });

  it('does not open media viewer for invalid attachments', () => {
    const { component } = createComponent();
    component.openMediaViewer({ id: 1, type: 'DOCUMENT', status: 'READY', url: 'u' } as any);
    expect(component.mediaViewer).toBeNull();
    component.openMediaViewer({ id: 1, type: 'IMAGE', status: 'FAILED', url: 'u' } as any);
    expect(component.mediaViewer).toBeNull();
  });

  it('openMediaViewer ignores attachments without url', () => {
    const { component } = createComponent();
    component.openMediaViewer({ id: 1, type: 'IMAGE', status: 'READY' } as any);
    expect(component.mediaViewer).toBeNull();
  });

  it('closes media viewer on keydown', () => {
    const { component } = createComponent();
    component.mediaViewer = { type: 'IMAGE', url: 'u' } as any;
    component.onMediaViewerKeydown({ key: 'Enter', preventDefault: jasmine.createSpy('pd') } as any);
    expect(component.mediaViewer).toBeNull();
  });

  it('marks upload failure when chunk upload errors', fakeAsync(() => {
    const { component } = createComponent();
    const file = new File(['hello'], 'note.txt', { type: 'text/plain' });
    const itemId = 'a1';
    component.attachments$.next([{
      id: itemId,
      file,
      type: 'DOCUMENT',
      displayName: 'note.txt',
      sizeBytes: file.size,
      altText: '',
      wasCompressed: false,
      status: 'UPLOADING',
      progress: 0,
      uploadId: 'upload1',
      attachmentId: 10,
      chunkSizeBytes: 1024
    } as any]);

    messageServiceSpy.uploadAttachmentChunk.and.returnValue(throwError(() => new Error('fail')));

    (component as any).startUpload(itemId);
    flushMicrotasks();
    tick(0);

    expect(component.attachmentItems[0].status).toBe('FAILED');
  }));

  it('handles conversation loading failures', () => {
    messageServiceSpy.getConversations.and.returnValue(throwError(() => new Error('fail')));
    const { component } = createComponent();

    component.loadConversations();

    expect(component.isLoadingConversations).toBeFalse();
    expect(component.errorMessage).toBe('Unable to load conversations.');
  });

  it('loads messages and marks conversations read', () => {
    const { component } = createComponent();
    const scrollSpy = spyOn(component as any, 'scrollToBottom');
    messageServiceSpy.getMessages.and.returnValue(of([{
      id: 5,
      conversationId: 44,
      senderUsername: 'bob',
      recipientUsername: 'alice',
      content: 'hello',
      createdAt: nowIso,
      isRead: false
    } as any]));

    component.loadMessages(44);

    expect(component.messages.length).toBe(1);
    expect(component.isLoadingMessages).toBeFalse();
    expect(scrollSpy).toHaveBeenCalled();
    expect(messageServiceSpy.markConversationRead).toHaveBeenCalledWith(44);
  });

  it('handles message load failures', () => {
    const { component } = createComponent();
    messageServiceSpy.getMessages.and.returnValue(throwError(() => new Error('fail')));

    component.loadMessages(12);

    expect(component.isLoadingMessages).toBeFalse();
    expect(component.messageLoadError).toBe('Unable to load messages.');
  });

  it('selectConversation navigates when selecting a different thread', () => {
    const { component } = createComponent();
    component.selectedConversationId = 1;

    component.selectConversation({ id: 2 } as any);

    expect(router.navigate).toHaveBeenCalledWith(['/messages', 2]);
  });

  it('selectConversation ignores active thread', () => {
    const { component } = createComponent();
    component.selectedConversationId = 3;

    component.selectConversation({ id: 3 } as any);

    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('validates empty content and oversized messages', async () => {
    const { component } = createComponent();
    component.recipientControl.setValue('bob');
    component.messageControl.setValue('');

    await component.sendMessage();

    expect(component.sendError).toBe('Message content or attachment is required.');

    component.messageControl.setValue('a'.repeat(component.maxMessageLength + 1));
    await component.sendMessage();

    expect(component.sendError).toContain('Message must be under');
  });

  it('sendMessage returns early when already sending', async () => {
    const { component } = createComponent();
    component.isSending = true;
    await component.sendMessage();
    expect(component.sendError).toBeNull();
  });

  it('sends messages without attachments and handles errors', () => {
    const { component } = createComponent();
    const upsertSpy = spyOn(component as any, 'upsertMessage').and.callThrough();
    const ensureSpy = spyOn(component as any, 'ensureConversationNavigation').and.callThrough();
    const reloadSpy = spyOn(component, 'loadConversations').and.callThrough();
    component.recipientControl.setValue('bob');
    component.messageControl.setValue('hello');

    component.sendMessage();

    expect(upsertSpy).toHaveBeenCalled();
    expect(ensureSpy).toHaveBeenCalled();
    expect(reloadSpy).toHaveBeenCalled();
    expect(component.messageControl.value).toBe('');

    messageServiceSpy.sendMessage.and.returnValue(throwError(() => new Error('fail')));
    component.recipientControl.setValue('bob');
    component.messageControl.setValue('retry');
    component.sendMessage();
    expect(component.sendError).toBe('Failed to send message.');
  });

  it('handles attachment session failures', async () => {
    const { component } = createComponent();
    const file = new File(['hello'], 'note.txt', { type: 'text/plain' });
    component.attachments$.next([{
      id: 'a1',
      file,
      type: 'DOCUMENT',
      displayName: 'note.txt',
      sizeBytes: file.size,
      altText: '',
      wasCompressed: false,
      status: 'DRAFT',
      progress: 0
    } as any]);
    component.recipientControl.setValue('bob');
    messageServiceSpy.createAttachmentUploadSessions.and.returnValue(throwError(() => new Error('fail')));

    await component.sendMessage();

    expect(component.sendError).toBe('Failed to start attachment upload.');
  });

  it('handles keydown send and typing events', fakeAsync(() => {
    const { component } = createComponent();
    component.selectedConversationId = 11;
    const sendSpy = spyOn(component, 'sendMessage').and.returnValue(Promise.resolve());
    const preventSpy = jasmine.createSpy('preventDefault');
    spyOn(component, 'canSend').and.returnValue(true);

    component.onMessageKeydown({ key: 'Enter', shiftKey: false, preventDefault: preventSpy } as any);

    expect(preventSpy).toHaveBeenCalled();
    expect(sendSpy).toHaveBeenCalled();

    component.onMessageInput();
    component.onMessageInput();
    expect(realtimeServiceSpy.sendTyping).toHaveBeenCalledWith(11, true);
    tick(1500);
    expect(realtimeServiceSpy.sendTyping).toHaveBeenCalledWith(11, false);
  }));

  it('does not send message on shift+enter', () => {
    const { component } = createComponent();
    const sendSpy = spyOn(component, 'sendMessage').and.returnValue(Promise.resolve());
    component.onMessageKeydown({ key: 'Enter', shiftKey: true, preventDefault: jasmine.createSpy('pd') } as any);
    expect(sendSpy).not.toHaveBeenCalled();
  });

  it('does not send typing when no conversation selected', () => {
    const { component } = createComponent();
    component.selectedConversationId = null;
    component.onMessageInput();
    expect(realtimeServiceSpy.sendTyping).not.toHaveBeenCalled();
  });

  it('triggers file picker and handles file selection', async () => {
    const { component } = createComponent();
    const inputElement = document.createElement('input');
    const clickSpy = spyOn(inputElement, 'click');
    component.fileInput = new ElementRef(inputElement);

    component.triggerFilePicker();
    expect(clickSpy).toHaveBeenCalled();

    const input = { files: null as File[] | null, value: '' } as any;
    await component.onFilesSelected({ target: input } as any);

    const maxFile = new File(['a'], 'doc.pdf', { type: 'application/pdf' });
    component.attachments$.next(new Array(component.maxAttachments).fill(null).map((_, index) => ({
      id: `draft-${index}`,
      file: maxFile,
      type: 'DOCUMENT',
      displayName: maxFile.name,
      sizeBytes: maxFile.size,
      altText: '',
      wasCompressed: false,
      status: 'DRAFT',
      progress: 0
    } as any)));
    input.files = [maxFile];
    await component.onFilesSelected({ target: input } as any);
    expect(component.sendError).toContain('attach up to');
  });

  it('rejects unsupported or oversized files and accepts valid ones', async () => {
    const { component } = createComponent();
    const input = { files: [] as File[] | null, value: '' } as any;
    const unsupported = new File(['a'], 'archive.zip', { type: 'application/zip' });
    input.files = [unsupported];

    await component.onFilesSelected({ target: input } as any);
    expect(component.sendError).toContain('not a supported type');

    const originalMax = component.maxDocumentBytes;
    (component as any).maxDocumentBytes = 1;
    const tooLarge = new File(['aa'], 'doc.pdf', { type: 'application/pdf' });
    input.files = [tooLarge];
    await component.onFilesSelected({ target: input } as any);
    expect(component.sendError).toContain('too large');

    (component as any).maxDocumentBytes = originalMax;
    const valid = new File(['ok'], 'note.txt', { type: 'text/plain' });
    const attachmentItem = {
      id: 'x1',
      file: valid,
      type: 'DOCUMENT',
      displayName: valid.name,
      sizeBytes: valid.size,
      altText: '',
      wasCompressed: false,
      status: 'DRAFT',
      progress: 0
    } as any;
    spyOn(component as any, 'buildAttachmentItem').and.resolveTo(attachmentItem);
    input.files = [valid];
    await component.onFilesSelected({ target: input } as any);
    expect(component.attachmentItems.length).toBe(1);
  });

  it('handles cancel upload errors', () => {
    const { component } = createComponent();
    messageServiceSpy.cancelAttachmentUpload.and.returnValue(throwError(() => new Error('fail')));
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x.txt',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'UPLOADING',
      progress: 0,
      uploadId: 'upload1'
    } as any]);

    component.cancelUpload('a1');

    expect(component.attachmentItems[0].status).toBe('FAILED');
    expect(component.attachmentItems[0].error).toBe('Failed to cancel.');
  });

  it('formats sizes, times, and progress helpers', () => {
    const { component } = createComponent();
    expect(component.formatFileSize(2048)).toContain('KB');
    expect(component.formatFileSize(2 * 1024 * 1024)).toContain('MB');
    expect(component.formatRelativeTime(null)).toBe('');
    const twoMinutesAgo = new Date(Date.now() - 2 * 60 * 1000).toISOString();
    expect(component.formatRelativeTime(twoMinutesAgo)).toBe('2m');
    expect(component.formatMessageTime(nowIso)).toContain(':');

    (component as any).uploadProgressByAttachmentId.set(10, 55);
    expect(component.getAttachmentProgress(10)).toBe(55);
    expect(component.getAttachmentProgress(undefined)).toBeNull();
  });

  it('handles typing indicator updates', fakeAsync(() => {
    const { component } = createComponent();
    component.selectedConversationId = 7;
    (component as any).handleTypingEvent({ conversationId: 7, senderUsername: 'bob', typing: true });
    expect(component.typingConversationId).toBe(7);
    expect(component.typingUsername).toBe('bob');
    tick(3000);
    expect(component.typingConversationId).toBeNull();

    (component as any).handleTypingEvent({ conversationId: 7, senderUsername: 'bob', typing: false });
    expect(component.typingConversationId).toBeNull();
  }));

  it('ignores typing events for other users or conversations', () => {
    const { component } = createComponent();
    component.selectedConversationId = 7;
    (component as any).handleTypingEvent({ conversationId: 7, senderUsername: 'alice', typing: true });
    expect(component.typingConversationId).toBeNull();
    (component as any).handleTypingEvent({ conversationId: 9, senderUsername: 'bob', typing: true });
    expect(component.typingConversationId).toBeNull();
  });

  it('shows typing indicator only for active conversation', () => {
    const { component } = createComponent();
    component.selectedConversationId = 5;
    component.typingConversationId = 5;
    expect(component.showTypingIndicator).toBeTrue();
    component.typingConversationId = 9;
    expect(component.showTypingIndicator).toBeFalse();
  });

  it('updates conversation previews and unread counts', () => {
    const { component } = createComponent();
    component.conversations = [{
      id: 55,
      participantUsername: 'bob',
      unreadCount: 0
    } as any];
    component.selectedConversationId = 55;
    const message = {
      id: 9,
      conversationId: 55,
      senderUsername: 'bob',
      recipientUsername: 'alice',
      content: 'hello there',
      attachments: [],
      createdAt: nowIso,
      isRead: false
    } as any;

    (component as any).handleIncomingMessage(message);

    expect(component.conversations[0].lastMessagePreview).toContain('hello');
    expect(component.conversations[0].unreadCount).toBe(0);

    const loadSpy = spyOn(component, 'loadConversations');
    (component as any).updateConversationPreview({ ...message, conversationId: 999 });
    expect(loadSpy).toHaveBeenCalled();
  });

  it('increments unread count for inactive conversations', () => {
    const { component } = createComponent();
    component.conversations = [{
      id: 12,
      participantUsername: 'carol',
      unreadCount: 0
    } as any];
    component.selectedConversationId = 99;
    const message = {
      id: 1,
      conversationId: 12,
      senderUsername: 'carol',
      recipientUsername: 'alice',
      content: 'hello',
      attachments: [],
      createdAt: nowIso,
      isRead: false
    } as any;

    (component as any).updateConversationPreview(message);

    expect(component.conversations[0].unreadCount).toBe(1);
  });

  it('builds video attachment items and reads metadata', async () => {
    const { component } = createComponent();
    const videoFile = new File(['a'], 'clip.mp4', { type: 'video/mp4' });
    const readSpy = spyOn(component as any, 'readVideoMetadata').and.resolveTo({ width: 640, height: 480, durationSeconds: 12 });
    const urlSpy = spyOn(URL, 'createObjectURL').and.returnValue('blob:video');

    const item = await (component as any).buildAttachmentItem(videoFile, 'VIDEO');

    expect(readSpy).toHaveBeenCalled();
    expect(urlSpy).toHaveBeenCalled();
    expect(item.width).toBe(640);
    expect(item.height).toBe(480);
    expect(item.durationSeconds).toBe(12);
  });

  it('compresses images when bitmap and canvas are available', async () => {
    const { component } = createComponent();
    const file = new File([new Uint8Array(10)], 'photo.jpg', { type: 'image/jpeg' });
    const originalCreateImageBitmap = (window as any).createImageBitmap;
    (window as any).createImageBitmap = async () => ({ width: 2000, height: 1000 });
    const originalCreateElement = document.createElement.bind(document);
    const canvasMock: any = {
      width: 0,
      height: 0,
      getContext: () => ({ drawImage: jasmine.createSpy('drawImage') }),
      toBlob: (cb: (blob: Blob | null) => void) => cb(new Blob([new Uint8Array(1)], { type: 'image/jpeg' }))
    };
    document.createElement = ((tag: string) => tag === 'canvas' ? canvasMock : originalCreateElement(tag)) as any;

    const result = await (component as any).compressImageFile(file);

    expect(result.wasCompressed).toBeTrue();
    expect(result.file.size).toBeLessThan(file.size);

    document.createElement = originalCreateElement as any;
    (window as any).createImageBitmap = originalCreateImageBitmap;
  });

  it('reads video metadata with mocked video element', async () => {
    const { component } = createComponent();
    const originalCreateElement = document.createElement.bind(document);
    let videoRef: any;
    document.createElement = ((tag: string) => {
      if (tag === 'video') {
        videoRef = {
          preload: '',
          muted: false,
          src: '',
          duration: 8.4,
          videoWidth: 800,
          videoHeight: 600,
          load: jasmine.createSpy('load'),
          onloadedmetadata: null,
          onerror: null
        };
        return videoRef;
      }
      return originalCreateElement(tag);
    }) as any;

    const promise = (component as any).readVideoMetadata('blob:video');
    videoRef.onloadedmetadata();
    const metadata = await promise;

    expect(metadata.width).toBe(800);
    expect(metadata.height).toBe(600);
    expect(metadata.durationSeconds).toBe(8);

    document.createElement = originalCreateElement as any;
  });

  it('creates attachment ids with crypto fallback', () => {
    const { component } = createComponent();
    const descriptor = Object.getOwnPropertyDescriptor(window, 'crypto');
    if (!descriptor || descriptor.configurable) {
      const originalCrypto = (window as any).crypto;
      const cryptoMock = {
        getRandomValues: (arr: Uint8Array) => {
          for (let i = 0; i < arr.length; i += 1) {
            arr[i] = i;
          }
          return arr;
        }
      };
      Object.defineProperty(window, 'crypto', { value: cryptoMock, configurable: true });

      const id = (component as any).createAttachmentId();
      expect(id.length).toBeGreaterThan(0);

      Object.defineProperty(window, 'crypto', { value: originalCrypto, configurable: true });
    } else {
      const id = (component as any).createAttachmentId();
      expect(id.length).toBeGreaterThan(0);
    }
  });

  it('ensures navigation scrolls when conversation is active', () => {
    const { component } = createComponent();
    component.selectedConversationId = 10;
    const scrollSpy = spyOn(component as any, 'scrollToBottom');

    (component as any).ensureConversationNavigation({ conversationId: 10 } as any);

    expect(scrollSpy).toHaveBeenCalled();
  });

  it('clears upload progress for finished attachments', () => {
    const { component } = createComponent();
    (component as any).uploadProgressByAttachmentId.set(1, 50);
    (component as any).uploadItemByAttachmentId.set(1, 'draft-1');
    const message = {
      id: 3,
      conversationId: 2,
      senderUsername: 'bob',
      recipientUsername: 'alice',
      createdAt: nowIso,
      isRead: false,
      attachments: [{ id: 1, status: 'READY' }]
    } as any;

    (component as any).syncUploadProgress(message);

    expect((component as any).uploadProgressByAttachmentId.has(1)).toBeFalse();
  });

  it('buildPreview returns empty when no content or attachments', () => {
    const { component } = createComponent();
    const preview = (component as any).buildPreview('   ', []);
    expect(preview).toBe('');
  });

  it('openMediaViewer ignores non-ready or unsupported attachments', () => {
    const { component } = createComponent();
    component.openMediaViewer({ status: 'UPLOADING', type: 'IMAGE', url: 'x' } as any);
    expect(component.mediaViewer).toBeNull();

    component.openMediaViewer({ status: 'READY', type: 'DOCUMENT', url: 'x' } as any);
    expect(component.mediaViewer).toBeNull();
  });

  it('openMediaViewer sets viewer for images', () => {
    const { component } = createComponent();
    component.openMediaViewer({
      status: 'READY',
      type: 'IMAGE',
      url: 'blob:image',
      altText: 'alt',
      originalFilename: 'photo.jpg'
    } as any);
    expect(component.mediaViewer?.url).toBe('blob:image');
    expect(component.mediaViewer?.type).toBe('IMAGE');
  });

  it('onMediaViewerKeydown closes viewer on action keys', () => {
    const { component } = createComponent();
    component.mediaViewer = { type: 'IMAGE', url: 'blob:image' };
    const preventSpy = jasmine.createSpy('preventDefault');

    component.onMediaViewerKeydown({ key: 'Escape', preventDefault: preventSpy } as any);
    expect(preventSpy).toHaveBeenCalled();
    expect(component.mediaViewer).toBeNull();
  });

  it('onMediaViewerKeydown ignores other keys', () => {
    const { component } = createComponent();
    component.mediaViewer = { type: 'IMAGE', url: 'blob:image' };
    const preventSpy = jasmine.createSpy('preventDefault');

    component.onMediaViewerKeydown({ key: 'a', preventDefault: preventSpy } as any);
    expect(preventSpy).not.toHaveBeenCalled();
    expect(component.mediaViewer).not.toBeNull();
  });

  it('updateAltText returns when target is missing', () => {
    const { component } = createComponent();
    const item = { id: 'a1', file: new File(['a'], 'photo.jpg', { type: 'image/jpeg' }), type: 'IMAGE', displayName: 'photo.jpg', sizeBytes: 10, altText: 'x', wasCompressed: false, status: 'DRAFT', progress: 0 } as any;
    component.attachments$.next([item]);

    component.updateAltText('a1', { target: null } as any);

    expect(component.attachmentItems[0].altText).toBe('x');
  });

  it('removeAttachment no-ops when item is missing', () => {
    const { component } = createComponent();
    component.attachments$.next([]);
    component.removeAttachment('missing');
    expect(component.attachmentItems.length).toBe(0);
  });

  it('builds document attachment items', async () => {
    const { component } = createComponent();
    const docFile = new File(['a'], 'note.txt', { type: 'text/plain' });
    const item = await (component as any).buildAttachmentItem(docFile, 'DOCUMENT');
    expect(item.type).toBe('DOCUMENT');
    expect(item.wasCompressed).toBeFalse();
  });

  it('builds gif attachment without compression', async () => {
    const { component } = createComponent();
    const gifFile = new File(['a'], 'anim.gif', { type: 'image/gif' });
    const urlSpy = spyOn(URL, 'createObjectURL').and.returnValue('blob:gif');

    const item = await (component as any).buildAttachmentItem(gifFile, 'IMAGE');

    expect(urlSpy).toHaveBeenCalled();
    expect(item.wasCompressed).toBeFalse();
  });

  it('handles missing bitmap support by sending original image', async () => {
    const { component } = createComponent();
    const file = new File([new Uint8Array(10)], 'photo.jpg', { type: 'image/jpeg' });
    const originalCreateImageBitmap = (window as any).createImageBitmap;
    (window as any).createImageBitmap = undefined;

    const result = await (component as any).compressImageFile(file);

    expect(result.wasCompressed).toBeFalse();
    expect(result.file).toBe(file);
    (window as any).createImageBitmap = originalCreateImageBitmap;
  });

  it('readVideoMetadata resolves empty on error', async () => {
    const { component } = createComponent();
    const originalCreateElement = document.createElement.bind(document);
    let videoRef: any;
    document.createElement = ((tag: string) => {
      if (tag === 'video') {
        videoRef = {
          preload: '',
          muted: false,
          src: '',
          duration: NaN,
          videoWidth: 0,
          videoHeight: 0,
          load: jasmine.createSpy('load'),
          onloadedmetadata: null,
          onerror: null
        };
        return videoRef;
      }
      return originalCreateElement(tag);
    }) as any;

    const promise = (component as any).readVideoMetadata('blob:video');
    videoRef.onerror();
    const metadata = await promise;

    expect(metadata.width).toBeUndefined();
    expect(metadata.height).toBeUndefined();

    document.createElement = originalCreateElement as any;
  });

  it('startUpload returns early when item is incomplete', fakeAsync(() => {
    const { component } = createComponent();
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x.txt',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'UPLOADING',
      progress: 0
    } as any]);

    (component as any).startUpload('a1');
    flushMicrotasks();

    expect(messageServiceSpy.uploadAttachmentChunk).not.toHaveBeenCalled();
  }));

  it('uploadAttachmentChunks returns early when item missing', fakeAsync(() => {
    const { component } = createComponent();
    (component as any).uploadAttachmentChunks('missing', 'upload', 10, new Subject<void>());
    flushMicrotasks();
    expect(messageServiceSpy.uploadAttachmentChunk).not.toHaveBeenCalled();
  }));

  it('handleTypingEvent ignores null event', () => {
    const { component } = createComponent();
    (component as any).handleTypingEvent(null as any);
    expect(component.typingConversationId).toBeNull();
  });

  it('getAttachmentLabel returns empty for ready', () => {
    const { component } = createComponent();
    expect(component.getAttachmentLabel({ status: 'READY' } as any)).toBe('');
  });

  it('previewForAttachment returns default when type missing', () => {
    const { component } = createComponent();
    const preview = (component as any).previewForAttachment({} as any);
    expect(preview).toBe('Attachment');
  });

  it('stopTypingSignal clears timers and sends stop typing', () => {
    const { component } = createComponent();
    component.selectedConversationId = 12;
    (component as any).typingActive = true;
    (component as any).typingSendTimeoutId = 123;
    spyOn(window, 'clearTimeout');

    (component as any).stopTypingSignal();

    expect(window.clearTimeout).toHaveBeenCalledWith(123);
    expect(realtimeServiceSpy.sendTyping).toHaveBeenCalledWith(12, false);
  });

  it('onMessageInput does not resend typing within interval', () => {
    const { component } = createComponent();
    component.selectedConversationId = 22;
    const now = Date.now();
    (component as any).typingActive = true;
    (component as any).typingLastSentAt = now;

    component.onMessageInput();

    expect(realtimeServiceSpy.sendTyping).not.toHaveBeenCalled();
  });

  it('onMessageInput returns early when no conversation selected', () => {
    const { component } = createComponent();
    component.selectedConversationId = null;
    component.onMessageInput();
    expect(realtimeServiceSpy.sendTyping).not.toHaveBeenCalled();
  });

  it('onMessageKeydown ignores shift+enter', () => {
    const { component } = createComponent();
    const sendSpy = spyOn(component, 'sendMessage');
    component.onMessageKeydown({ key: 'Enter', shiftKey: true, preventDefault: jasmine.createSpy('pd') } as any);
    expect(sendSpy).not.toHaveBeenCalled();
  });

  it('onMessageKeydown does not send when canSend is false', () => {
    const { component } = createComponent();
    const canSendSpy = spyOn(component as any, 'canSend').and.returnValue(false);
    const sendSpy = spyOn(component, 'sendMessage');
    const preventSpy = jasmine.createSpy('pd');

    component.onMessageKeydown({ key: 'Enter', shiftKey: false, preventDefault: preventSpy } as any);

    expect(preventSpy).toHaveBeenCalled();
    expect(canSendSpy).toHaveBeenCalled();
    expect(sendSpy).not.toHaveBeenCalled();
  });

  it('clearAttachments revokes previews', () => {
    const { component } = createComponent();
    const revokeSpy = spyOn(URL, 'revokeObjectURL');
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'DRAFT',
      progress: 0,
      previewUrl: 'blob:test'
    } as any]);

    (component as any).clearAttachments();

    expect(revokeSpy).toHaveBeenCalledWith('blob:test');
    expect(component.attachmentItems.length).toBe(0);
  });

  it('cancelUpload returns when item missing or no uploadId', () => {
    const { component } = createComponent();
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x.txt',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'UPLOADING',
      progress: 0
    } as any]);

    component.cancelUpload('missing');
    component.cancelUpload('a1');

    expect(messageServiceSpy.cancelAttachmentUpload).not.toHaveBeenCalled();
  });

  it('cancelUpload cancels active upload and removes attachment', () => {
    const { component } = createComponent();
    const cancelSpy = jasmine.createSpy('cancel');
    const completeSpy = jasmine.createSpy('complete');
    const unsubscribeSpy = jasmine.createSpy('unsubscribe');
    const cancel$ = { isStopped: false, next: cancelSpy, complete: completeSpy } as any;
    (component as any).uploadCancelMap.set('a1', cancel$);
    (component as any).uploadSubscriptions.set('a1', { unsubscribe: unsubscribeSpy });
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x.txt',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'UPLOADING',
      progress: 0,
      uploadId: 'upload1'
    } as any]);

    component.cancelUpload('a1');

    expect(cancelSpy).toHaveBeenCalled();
    expect(completeSpy).toHaveBeenCalled();
    expect(unsubscribeSpy).toHaveBeenCalled();
    expect(messageServiceSpy.cancelAttachmentUpload).toHaveBeenCalledWith('upload1');
    expect(component.attachmentItems.length).toBe(0);
  });

  it('triggerFilePicker clicks input when available', () => {
    const { component } = createComponent();
    const input = document.createElement('input');
    const clickSpy = spyOn(input, 'click');
    component.fileInput = new ElementRef(input);

    component.triggerFilePicker();

    expect(clickSpy).toHaveBeenCalled();
  });

  it('removeAttachment revokes preview urls', () => {
    const { component } = createComponent();
    const revokeSpy = spyOn(URL, 'revokeObjectURL');
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x.txt',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'DRAFT',
      progress: 0,
      previewUrl: 'blob:preview'
    } as any]);

    component.removeAttachment('a1');

    expect(revokeSpy).toHaveBeenCalledWith('blob:preview');
    expect(component.attachmentItems.length).toBe(0);
  });

  it('retryUpload returns early when not failed', () => {
    const { component } = createComponent();
    component.attachments$.next([{
      id: 'a1',
      file: new File(['x'], 'x.txt'),
      type: 'DOCUMENT',
      displayName: 'x.txt',
      sizeBytes: 1,
      altText: '',
      wasCompressed: false,
      status: 'UPLOADING',
      progress: 0,
      uploadId: 'u1'
    } as any]);
    spyOn(component as any, 'startUpload');

    component.retryUpload('a1');

    expect((component as any).startUpload).not.toHaveBeenCalled();
  });

  it('ensureConversationNavigation navigates when no selection', () => {
    const { component } = createComponent();
    component.selectedConversationId = null;

    (component as any).ensureConversationNavigation({ conversationId: 44 } as any);

    expect(router.navigate).toHaveBeenCalledWith(['/messages', 44]);
  });

  it('updateConversationPreview keeps unread count for outgoing message', () => {
    const { component } = createComponent();
    component.conversations = [{ id: 5, participantUsername: 'bob', unreadCount: 2 } as any];
    component.selectedConversationId = 10;

    (component as any).updateConversationPreview({
      id: 1,
      conversationId: 5,
      senderUsername: 'alice',
      recipientUsername: 'bob',
      content: 'hello',
      attachments: [],
      createdAt: nowIso,
      isRead: false
    } as any);

    expect(component.conversations[0].unreadCount).toBe(2);
  });
});
