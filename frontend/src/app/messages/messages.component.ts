import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, Subject, Subscription, firstValueFrom } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AuthService } from '../auth/auth.service';
import {
  AttachmentType,
  AttachmentUploadRequest,
  AttachmentUploadSessionResponse,
  Conversation,
  Message,
  MessageAttachment,
  MessageService
} from './message.service';
import { MessageRealtimeService, TypingEvent } from './message-realtime.service';

interface AttachmentItem {
  id: string;
  file: File;
  type: AttachmentType;
  previewUrl?: string;
  displayName: string;
  sizeBytes: number;
  altText: string;
  wasCompressed: boolean;
  originalSizeBytes?: number;
  width?: number | null;
  height?: number | null;
  durationSeconds?: number | null;
  status: 'DRAFT' | 'UPLOADING' | 'FINALIZING' | 'FAILED' | 'COMPLETE';
  progress: number;
  uploadId?: string;
  attachmentId?: number;
  chunkSizeBytes?: number;
  error?: string | null;
}

interface MediaViewerState {
  type: 'IMAGE' | 'VIDEO';
  url: string;
  altText?: string | null;
  filename?: string | null;
}

@Component({
  selector: 'app-messages',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './messages.component.html',
  styleUrl: './messages.component.scss'
})
export class MessagesComponent implements OnInit, OnDestroy {
  conversations: Conversation[] = [];
  messages: Message[] = [];
  selectedConversationId: number | null = null;
  selectedConversation: Conversation | null = null;
  isLoadingConversations = false;
  isLoadingMessages = false;
  isSending = false;
  errorMessage: string | null = null;
  messageLoadError: string | null = null;
  sendError: string | null = null;
  isComposingNew = false;
  recipientControl: FormControl<string>;
  messageControl: FormControl<string>;
  readonly maxMessageLength = 2000;
  readonly previewLimit = 120;
  readonly maxAttachments = 6;
  readonly maxImageBytes = 10 * 1024 * 1024;
  readonly maxVideoBytes = 50 * 1024 * 1024;
  readonly maxDocumentBytes = 20 * 1024 * 1024;
  readonly imageMaxDimension = 1080;
  readonly imageCompressionQuality = 0.8;
  readonly maxAltTextLength = 200;
  private readonly imageTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);
  private readonly videoTypes = new Set(['video/mp4', 'video/webm', 'video/quicktime']);
  private readonly documentTypes = new Set([
    'application/pdf',
    'text/plain',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'application/vnd.ms-powerpoint',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation'
  ]);

  attachments$ = new BehaviorSubject<AttachmentItem[]>([]);
  expireAttachments = false;
  typingConversationId: number | null = null;
  typingUsername: string | null = null;
  private typingTimeoutId: number | null = null;
  private typingSendTimeoutId: number | null = null;
  private typingActive = false;
  private typingLastSentAt = 0;
  private readonly typingSendIntervalMs = 800;
  private readonly destroy$ = new Subject<void>();
  private readonly uploadProgressByAttachmentId = new Map<number, number>();
  private readonly uploadItemByAttachmentId = new Map<number, string>();
  private readonly uploadCancelMap = new Map<string, Subject<void>>();
  private readonly uploadSubscriptions = new Map<string, Subscription>();
  private pendingRecipientUsername: string | null = null;
  private attachmentIdCounter = 0;

  mediaViewer: MediaViewerState | null = null;

  @ViewChild('messageScroll') messageScroll?: ElementRef<HTMLDivElement>;
  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;

  constructor(
    private messageService: MessageService,
    private messageRealtimeService: MessageRealtimeService,
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder
  ) {
    this.recipientControl = this.fb.control('', { nonNullable: true });
    this.messageControl = this.fb.control('', { nonNullable: true });
  }

  get currentUsername(): string | null {
    return this.authService.getUsername();
  }

  get messageLength(): number {
    return this.messageControl.value.length;
  }

  get attachmentItems(): AttachmentItem[] {
    return this.attachments$.value;
  }

  ngOnInit(): void {
    this.loadConversations();
    this.messageRealtimeService.connect();
    this.messageRealtimeService.onMessage()
      .pipe(takeUntil(this.destroy$))
      .subscribe(message => {
        this.handleIncomingMessage(message);
      });
    this.messageRealtimeService.onTyping()
      .pipe(takeUntil(this.destroy$))
      .subscribe(event => {
        this.handleTypingEvent(event);
      });
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const rawId = params.get('conversationId');
      const parsedId = rawId ? Number(rawId) : Number.NaN;
      if (this.selectedConversationId !== parsedId) {
        this.stopTypingSignal();
        this.clearTypingIndicator();
      }
      this.selectedConversationId = Number.isFinite(parsedId) ? parsedId : null;
      if (this.selectedConversationId !== null) {
        this.loadMessages(this.selectedConversationId);
        this.isComposingNew = false;
      } else {
        this.messages = [];
        this.messageLoadError = null;
        this.sendError = null;
      }
      this.syncSelectedConversation();
    });
    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const recipient = (params.get('recipient') || '').trim();
      this.pendingRecipientUsername = recipient.length > 0 ? recipient : null;
      this.applyRecipientNavigation();
    });
  }

  loadConversations(): void {
    this.isLoadingConversations = true;
    this.errorMessage = null;
    this.messageService.getConversations().subscribe({
      next: (conversations) => {
        this.conversations = conversations || [];
        this.syncSelectedConversation();
        this.applyRecipientNavigation();
        this.isLoadingConversations = false;
      },
      error: (error) => {
        console.error('Failed to load conversations', error);
        this.isLoadingConversations = false;
        this.errorMessage = 'Unable to load conversations.';
      }
    });
  }

  loadMessages(conversationId: number): void {
    this.isLoadingMessages = true;
    this.messageLoadError = null;
    this.messageService.getMessages(conversationId).subscribe({
      next: (messages) => {
        this.messages = messages || [];
        this.isLoadingMessages = false;
        this.scrollToBottom();
        this.markConversationRead(conversationId);
      },
      error: (error) => {
        console.error('Failed to load messages', error);
        this.isLoadingMessages = false;
        this.messageLoadError = 'Unable to load messages.';
      }
    });
  }

  selectConversation(conversation: Conversation): void {
    if (this.selectedConversationId === conversation.id) {
      return;
    }
    this.router.navigate(['/messages', conversation.id]);
  }

  startNewMessage(): void {
    this.router.navigate(['/messages']);
    this.stopTypingSignal();
    this.selectedConversationId = null;
    this.selectedConversation = null;
    this.messages = [];
    this.messageLoadError = null;
    this.sendError = null;
    this.clearAttachments();
    this.isComposingNew = true;
    this.clearTypingIndicator();
    this.recipientControl.setValue('');
    this.messageControl.setValue('');
  }

  async sendMessage(): Promise<void> {
    if (this.isSending) {
      return;
    }
    const recipient = this.selectedConversation
      ? this.selectedConversation.participantUsername
      : this.recipientControl.value.trim();
    const content = this.messageControl.value.trim();
    const draftAttachments = this.attachmentItems.filter(item => item.status === 'DRAFT');
    const hasAttachments = draftAttachments.length > 0;

    if (!recipient) {
      this.sendError = 'Recipient username is required.';
      return;
    }
    if (!content && !hasAttachments) {
      this.sendError = 'Message content or attachment is required.';
      return;
    }
    if (content.length > this.maxMessageLength) {
      this.sendError = `Message must be under ${this.maxMessageLength} characters.`;
      return;
    }

    this.stopTypingSignal();
    this.isSending = true;
    this.sendError = null;

    if (hasAttachments) {
      try {
        const request = {
          recipientUsername: recipient,
          content: content || null,
          expiresInSeconds: this.expireAttachments ? 24 * 60 * 60 : null,
          attachments: this.buildUploadRequests(draftAttachments)
        };
        const response = await firstValueFrom(this.messageService.createAttachmentUploadSessions(request));
        const message = response.message;
        this.isSending = false;
        this.messageControl.setValue('');
        this.stopTypingSignal();
        this.upsertMessage(message);
        this.ensureConversationNavigation(message);
        this.loadConversations();

        const sessionMap = new Map<string, AttachmentUploadSessionResponse>();
        draftAttachments.forEach((item, index) => {
          if (response.uploads[index]) {
            sessionMap.set(item.id, response.uploads[index]);
          }
        });

        const updated = this.attachmentItems.map(item => {
          const session = sessionMap.get(item.id);
          if (!session) {
            return item;
          }
          const updatedItem = {
            ...item,
            status: 'UPLOADING' as const,
            progress: 0,
            uploadId: session.uploadId,
            attachmentId: session.attachmentId,
            chunkSizeBytes: session.chunkSizeBytes,
            error: null
          };
          this.uploadItemByAttachmentId.set(session.attachmentId, item.id);
          this.uploadProgressByAttachmentId.set(session.attachmentId, 0);
          return updatedItem;
        });
        this.attachments$.next(updated);

        draftAttachments.forEach((item) => {
          const session = sessionMap.get(item.id);
          if (session) {
            this.startUpload(item.id);
          }
        });
        return;
      } catch (error) {
        console.error('Failed to send message with attachments', error);
        this.isSending = false;
        this.sendError = 'Failed to start attachment upload.';
        return;
      }
    }

    this.messageService.sendMessage({ recipientUsername: recipient, content }).subscribe({
      next: (message) => {
        this.isSending = false;
        this.messageControl.setValue('');
        this.stopTypingSignal();
        this.upsertMessage(message);
        this.ensureConversationNavigation(message);
        this.loadConversations();
      },
      error: (error) => {
        console.error('Failed to send message', error);
        this.isSending = false;
        this.sendError = 'Failed to send message.';
      }
    });
  }

  onMessageKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      if (this.canSend()) {
        this.sendMessage();
      }
    }
  }

  onMessageInput(): void {
    if (!this.selectedConversationId) {
      return;
    }
    const now = Date.now();
    if (!this.typingActive || now - this.typingLastSentAt >= this.typingSendIntervalMs) {
      this.typingActive = true;
      this.typingLastSentAt = now;
      this.messageRealtimeService.sendTyping(this.selectedConversationId, true);
    }
    if (this.typingSendTimeoutId) {
      window.clearTimeout(this.typingSendTimeoutId);
    }
    this.typingSendTimeoutId = window.setTimeout(() => {
      this.stopTypingSignal();
    }, 1500);
  }

  onMessageBlur(): void {
    this.stopTypingSignal();
  }

  triggerFilePicker(): void {
    this.fileInput?.nativeElement?.click();
  }

  async onFilesSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement | null;
    if (!input?.files || input.files.length === 0) {
      return;
    }
    this.sendError = null;
    const selectedFiles = Array.from(input.files);
    for (const file of selectedFiles) {
      const draftCount = this.attachmentItems.filter(item => item.status === 'DRAFT').length;
      if (draftCount >= this.maxAttachments) {
        this.sendError = `You can attach up to ${this.maxAttachments} files.`;
        break;
      }
      const type = this.resolveAttachmentType(file);
      if (!type) {
        this.sendError = `File ${file.name} is not a supported type.`;
        continue;
      }
      const maxBytes = this.maxBytesForType(type);
      if (file.size > maxBytes) {
        this.sendError = `File ${file.name} is too large. Max size is ${this.formatFileSize(maxBytes)}.`;
        continue;
      }
      const item = await this.buildAttachmentItem(file, type);
      if (item) {
        this.attachments$.next([...this.attachmentItems, item]);
      }
    }
    input.value = '';
  }

  removeAttachment(id: string): void {
    const item = this.attachmentItems.find(attachment => attachment.id === id);
    if (!item) {
      return;
    }
    if (item.previewUrl) {
      URL.revokeObjectURL(item.previewUrl);
    }
    this.attachments$.next(this.attachmentItems.filter(attachment => attachment.id !== id));
  }

  cancelUpload(id: string): void {
    const item = this.attachmentItems.find(attachment => attachment.id === id);
    if (!item || !item.uploadId) {
      return;
    }
    const cancel$ = this.uploadCancelMap.get(id);
    if (cancel$ && !cancel$.isStopped) {
      cancel$.next();
      cancel$.complete();
    }
    const subscription = this.uploadSubscriptions.get(id);
    subscription?.unsubscribe();
    this.uploadSubscriptions.delete(id);
    this.uploadCancelMap.delete(id);
    this.messageService.cancelAttachmentUpload(item.uploadId).subscribe({
      next: () => {
        this.removeAttachment(id);
      },
      error: (error) => {
        console.error('Failed to cancel upload', error);
        this.updateAttachment(id, (existing) => ({ ...existing, status: 'FAILED', error: 'Failed to cancel.' }));
      }
    });
  }

  retryUpload(id: string): void {
    const item = this.attachmentItems.find(attachment => attachment.id === id);
    if (!item || !item.uploadId || item.status !== 'FAILED') {
      return;
    }
    this.updateAttachment(id, (existing) => ({ ...existing, status: 'UPLOADING', progress: 0, error: null }));
    if (item.attachmentId) {
      this.uploadProgressByAttachmentId.set(item.attachmentId, 0);
    }
    const cancel$ = this.uploadCancelMap.get(id);
    if (cancel$ && !cancel$.isStopped) {
      cancel$.complete();
    }
    this.uploadCancelMap.delete(id);
    this.startUpload(id);
  }

  updateAltText(id: string, event: Event): void {
    const target = event.target as HTMLInputElement | null;
    if (!target) {
      return;
    }
    let value = target.value || '';
    value = value.replace(/[<>]/g, '');
    if (value.length > this.maxAltTextLength) {
      value = value.substring(0, this.maxAltTextLength);
    }
    this.updateAttachment(id, (item) => ({ ...item, altText: value }));
  }

  openMediaViewer(attachment: MessageAttachment): void {
    if (!attachment.url || (attachment.status !== 'READY')) {
      return;
    }
    if (attachment.type !== 'IMAGE' && attachment.type !== 'VIDEO') {
      return;
    }
    this.mediaViewer = {
      type: attachment.type,
      url: attachment.url,
      altText: attachment.altText,
      filename: attachment.originalFilename
    };
  }

  closeMediaViewer(): void {
    this.mediaViewer = null;
  }

  onMediaViewerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ' || event.key === 'Escape') {
      event.preventDefault();
      this.closeMediaViewer();
    }
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    const kb = bytes / 1024;
    if (kb < 1024) {
      return `${kb.toFixed(1)} KB`;
    }
    return `${(kb / 1024).toFixed(1)} MB`;
  }

  getAttachmentProgress(attachmentId: number | undefined): number | null {
    if (!attachmentId) {
      return null;
    }
    return this.uploadProgressByAttachmentId.get(attachmentId) ?? null;
  }

  canSend(): boolean {
    const recipient = this.selectedConversation
      ? this.selectedConversation.participantUsername
      : this.recipientControl.value.trim();
    const hasContent = this.messageControl.value.trim().length > 0;
    const hasDraftAttachments = this.attachmentItems.some(item => item.status === 'DRAFT');
    return !!recipient && (hasContent || hasDraftAttachments) && !this.isSending;
  }

  isOutgoing(message: Message): boolean {
    return message.senderUsername === this.currentUsername;
  }

  trackByConversationId(_: number, conversation: Conversation): number {
    return conversation.id;
  }

  trackByMessageId(_: number, message: Message): number {
    return message.id;
  }

  trackByAttachmentId(_: number, attachment: MessageAttachment): number {
    return attachment.id;
  }

  trackByAttachmentItemId(_: number, item: AttachmentItem): string {
    return item.id;
  }

  formatRelativeTime(timestamp?: string | null): string {
    if (!timestamp) {
      return '';
    }
    const date = new Date(timestamp);
    const diffMs = Date.now() - date.getTime();
    const minute = 60 * 1000;
    const hour = 60 * minute;
    const day = 24 * hour;
    if (diffMs < minute) {
      return 'now';
    }
    if (diffMs < hour) {
      return `${Math.floor(diffMs / minute)}m`;
    }
    if (diffMs < day) {
      return `${Math.floor(diffMs / hour)}h`;
    }
    return `${Math.floor(diffMs / day)}d`;
  }

  formatMessageTime(timestamp: string): string {
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  getConversationPreview(conversation: Conversation): string {
    if (conversation.lastMessagePreview) {
      const prefix = conversation.lastMessageSenderUsername === this.currentUsername ? 'You: ' : '';
      return `${prefix}${conversation.lastMessagePreview}`;
    }
    return 'Say hello and start the conversation.';
  }

  getAttachmentLabel(attachment: MessageAttachment): string {
    if (attachment.status === 'EXPIRED') {
      return 'Expired attachment';
    }
    if (attachment.status === 'FAILED') {
      return 'Attachment failed';
    }
    if (attachment.status === 'QUARANTINED') {
      return 'Attachment quarantined';
    }
    if (attachment.status === 'UPLOADING') {
      return 'Uploading';
    }
    return '';
  }

  getAttachmentStatusClass(attachment: MessageAttachment): string {
    return `status-${attachment.status.toLowerCase()}`;
  }

  private applyRecipientNavigation(): void {
    const recipient = this.pendingRecipientUsername;
    if (!recipient) {
      return;
    }
    if (this.selectedConversationId !== null) {
      return;
    }
    const existing = this.findConversationByUsername(recipient);
    if (existing && !this.hasDraft()) {
      this.router.navigate(['/messages', existing.id]);
      return;
    }
    if (!this.isComposingNew || this.recipientControl.value.trim() !== recipient) {
      this.prepareComposer(recipient);
    }
  }

  private findConversationByUsername(username: string): Conversation | undefined {
    const normalized = username.trim().toLowerCase();
    if (!normalized) {
      return undefined;
    }
    return this.conversations.find(conversation => conversation.participantUsername.toLowerCase() === normalized);
  }

  private prepareComposer(recipient: string): void {
    const normalized = recipient.trim();
    if (!normalized) {
      return;
    }
    this.stopTypingSignal();
    this.selectedConversationId = null;
    this.selectedConversation = null;
    this.messages = [];
    this.messageLoadError = null;
    this.sendError = null;
    this.clearAttachments();
    this.isComposingNew = true;
    this.clearTypingIndicator();
    this.recipientControl.setValue(normalized);
    this.messageControl.setValue('');
  }

  private hasDraft(): boolean {
    return this.messageControl.value.trim().length > 0 || this.attachmentItems.length > 0;
  }

  private syncSelectedConversation(): void {
    if (this.selectedConversationId === null) {
      this.selectedConversation = null;
      return;
    }
    this.selectedConversation = this.conversations.find(conversation => conversation.id === this.selectedConversationId) || null;
  }

  private markConversationRead(conversationId: number): void {
    this.messageService.markConversationRead(conversationId).subscribe({
      next: () => {
        this.conversations = this.conversations.map(conversation =>
          conversation.id === conversationId ? { ...conversation, unreadCount: 0 } : conversation
        );
        if (this.selectedConversation?.id === conversationId) {
          this.selectedConversation = { ...this.selectedConversation, unreadCount: 0 };
        }
      },
      error: (error) => {
        console.error('Failed to mark conversation read', error);
      }
    });
  }

  private scrollToBottom(): void {
    if (!this.messageScroll?.nativeElement) {
      return;
    }
    setTimeout(() => {
      const element = this.messageScroll?.nativeElement;
      if (element) {
        element.scrollTop = element.scrollHeight;
      }
    }, 0);
  }

  ngOnDestroy(): void {
    this.stopTypingSignal();
    this.clearTypingIndicator();
    this.clearAttachments();
    this.messageRealtimeService.disconnect();
    this.destroy$.next();
    this.destroy$.complete();
  }

  get showTypingIndicator(): boolean {
    return this.typingConversationId !== null && this.typingConversationId === this.selectedConversationId;
  }

  private handleIncomingMessage(message: Message): void {
    const isInActiveConversation = this.selectedConversationId === message.conversationId;
    this.upsertMessage(message);
    this.syncUploadProgress(message);
    if (isInActiveConversation) {
      this.scrollToBottom();
      if (!this.isOutgoing(message)) {
        this.markConversationRead(message.conversationId);
      }
    }
    this.updateConversationPreview(message);
  }

  private handleTypingEvent(event: TypingEvent): void {
    if (!event || !event.conversationId) {
      return;
    }
    if (event.senderUsername === this.currentUsername) {
      return;
    }
    if (this.selectedConversationId !== event.conversationId) {
      return;
    }
    if (!event.typing) {
      this.clearTypingIndicator();
      return;
    }
    this.typingConversationId = event.conversationId;
    this.typingUsername = event.senderUsername;
    if (this.typingTimeoutId) {
      window.clearTimeout(this.typingTimeoutId);
    }
    this.typingTimeoutId = window.setTimeout(() => {
      this.clearTypingIndicator();
    }, 3000);
  }

  private upsertMessage(message: Message): void {
    const index = this.messages.findIndex(existing => existing.id === message.id);
    if (index === -1) {
      this.messages = [...this.messages, message];
      return;
    }
    const updated = [...this.messages];
    updated[index] = message;
    this.messages = updated;
  }

  private updateConversationPreview(message: Message): void {
    const conversation = this.conversations.find(item => item.id === message.conversationId);
    if (!conversation) {
      this.loadConversations();
      return;
    }
    const isOutgoing = this.isOutgoing(message);
    conversation.lastMessagePreview = this.buildPreview(message.content, message.attachments);
    conversation.lastMessageAt = message.createdAt;
    conversation.lastMessageSenderUsername = message.senderUsername;
    if (!isOutgoing && this.selectedConversationId !== message.conversationId) {
      conversation.unreadCount = (conversation.unreadCount || 0) + 1;
    } else if (this.selectedConversationId === message.conversationId) {
      conversation.unreadCount = 0;
    }
    this.conversations = [
      conversation,
      ...this.conversations.filter(item => item.id !== conversation.id)
    ];
    this.syncSelectedConversation();
  }

  private buildPreview(content?: string | null, attachments?: MessageAttachment[]): string {
    const normalizedContent = content ? content.trim() : '';
    if (normalizedContent.length > 0) {
      const normalized = normalizedContent.replace(/\s+/g, ' ');
      if (normalized.length <= this.previewLimit) {
        return normalized;
      }
      return `${normalized.substring(0, this.previewLimit).trim()}...`;
    }
    if (!attachments || attachments.length === 0) {
      return '';
    }
    if (attachments.length === 1) {
      return this.previewForAttachment(attachments[0]);
    }
    return `${attachments.length} attachments`;
  }

  private previewForAttachment(attachment: MessageAttachment): string {
    if (!attachment || !attachment.type) {
      return 'Attachment';
    }
    switch (attachment.type) {
      case 'IMAGE':
        return 'Photo';
      case 'VIDEO':
        return 'Video';
      case 'DOCUMENT':
        return 'Document';
      default:
        return 'Attachment';
    }
  }

  private stopTypingSignal(): void {
    if (this.typingSendTimeoutId) {
      window.clearTimeout(this.typingSendTimeoutId);
      this.typingSendTimeoutId = null;
    }
    if (this.typingActive && this.selectedConversationId) {
      this.messageRealtimeService.sendTyping(this.selectedConversationId, false);
    }
    this.typingActive = false;
  }

  private clearTypingIndicator(): void {
    if (this.typingTimeoutId) {
      window.clearTimeout(this.typingTimeoutId);
      this.typingTimeoutId = null;
    }
    this.typingConversationId = null;
    this.typingUsername = null;
  }

  private clearAttachments(): void {
    this.attachmentItems.forEach((attachment) => {
      if (attachment.previewUrl) {
        URL.revokeObjectURL(attachment.previewUrl);
      }
    });
    this.attachments$.next([]);
  }

  private resolveAttachmentType(file: File): AttachmentType | null {
    const contentType = (file.type || '').toLowerCase();
    const name = file.name.toLowerCase();
    if (this.imageTypes.has(contentType) || this.hasExtension(name, ['.jpg', '.jpeg', '.png', '.webp', '.gif'])) {
      return 'IMAGE';
    }
    if (this.videoTypes.has(contentType) || this.hasExtension(name, ['.mp4', '.webm', '.mov'])) {
      return 'VIDEO';
    }
    if (this.documentTypes.has(contentType) || this.hasExtension(name, ['.pdf', '.txt', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx'])) {
      return 'DOCUMENT';
    }
    return null;
  }

  private maxBytesForType(type: AttachmentType): number {
    switch (type) {
      case 'IMAGE':
        return this.maxImageBytes;
      case 'VIDEO':
        return this.maxVideoBytes;
      case 'DOCUMENT':
        return this.maxDocumentBytes;
    }
  }

  private hasExtension(filename: string, extensions: string[]): boolean {
    return extensions.some((extension) => filename.endsWith(extension));
  }

  private async buildAttachmentItem(file: File, type: AttachmentType): Promise<AttachmentItem | null> {
    if (type === 'IMAGE') {
      const compressed = await this.compressImageFile(file);
      const previewUrl = URL.createObjectURL(compressed.file);
      return {
        id: this.createAttachmentId(),
        file: compressed.file,
        type,
        previewUrl,
        displayName: compressed.file.name,
        sizeBytes: compressed.file.size,
        altText: '',
        wasCompressed: compressed.wasCompressed,
        originalSizeBytes: compressed.wasCompressed ? file.size : undefined,
        width: compressed.width,
        height: compressed.height,
        status: 'DRAFT',
        progress: 0
      };
    }

    if (type === 'VIDEO') {
      const previewUrl = URL.createObjectURL(file);
      const metadata = await this.readVideoMetadata(previewUrl);
      return {
        id: this.createAttachmentId(),
        file,
        type,
        previewUrl,
        displayName: file.name,
        sizeBytes: file.size,
        altText: '',
        wasCompressed: false,
        width: metadata.width,
        height: metadata.height,
        durationSeconds: metadata.durationSeconds,
        status: 'DRAFT',
        progress: 0
      };
    }

    return {
      id: this.createAttachmentId(),
      file,
      type,
      displayName: file.name,
      sizeBytes: file.size,
      altText: '',
      wasCompressed: false,
      status: 'DRAFT',
      progress: 0
    };
  }

  private async compressImageFile(file: File): Promise<{ file: File; wasCompressed: boolean; width?: number; height?: number }> {
    if (file.type === 'image/gif') {
      return { file, wasCompressed: false };
    }
    try {
      if (typeof createImageBitmap === 'undefined') {
        return { file, wasCompressed: false };
      }
      let imageBitmap: ImageBitmap;
      try {
        imageBitmap = await createImageBitmap(file, { imageOrientation: 'from-image' } as ImageBitmapOptions);
      } catch {
        imageBitmap = await createImageBitmap(file);
      }
      const scale = Math.min(1, this.imageMaxDimension / Math.max(imageBitmap.width, imageBitmap.height));
      const targetWidth = Math.round(imageBitmap.width * scale);
      const targetHeight = Math.round(imageBitmap.height * scale);
      const canvas = document.createElement('canvas');
      canvas.width = targetWidth;
      canvas.height = targetHeight;
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        return { file, wasCompressed: false };
      }
      ctx.drawImage(imageBitmap, 0, 0, targetWidth, targetHeight);
      const outputType = file.type || 'image/jpeg';
      const quality = outputType === 'image/jpeg' || outputType === 'image/webp'
        ? this.imageCompressionQuality
        : undefined;
      const blob = await new Promise<Blob | null>((resolve) =>
        canvas.toBlob(resolve, outputType, quality)
      );
      if (!blob || blob.size >= file.size) {
        return { file, wasCompressed: false, width: targetWidth, height: targetHeight };
      }
      const compressedFile = new File([blob], file.name, { type: outputType, lastModified: file.lastModified });
      return { file: compressedFile, wasCompressed: true, width: targetWidth, height: targetHeight };
    } catch (error) {
      console.warn('Image compression failed, sending original file.', error);
      return { file, wasCompressed: false };
    }
  }

  private async readVideoMetadata(previewUrl: string): Promise<{ width?: number; height?: number; durationSeconds?: number }> {
    return new Promise((resolve) => {
      const video = document.createElement('video');
      video.preload = 'metadata';
      video.muted = true;
      video.src = previewUrl;
      video.load();
      const cleanup = () => {
        video.src = '';
      };
      video.onloadedmetadata = () => {
        const durationSeconds = Number.isFinite(video.duration) ? Math.round(video.duration) : undefined;
        resolve({
          width: video.videoWidth || undefined,
          height: video.videoHeight || undefined,
          durationSeconds
        });
        cleanup();
      };
      video.onerror = () => {
        resolve({});
        cleanup();
      };
    });
  }

  private buildUploadRequests(items: AttachmentItem[]): AttachmentUploadRequest[] {
    return items.map((item) => ({
      fileName: item.file.name,
      mimeType: item.file.type || this.mimeFromType(item.type),
      sizeBytes: item.file.size,
      width: item.width ?? undefined,
      height: item.height ?? undefined,
      durationSeconds: item.durationSeconds ?? undefined,
      altText: item.type === 'IMAGE' ? item.altText || null : null
    }));
  }

  private mimeFromType(type: AttachmentType): string {
    switch (type) {
      case 'IMAGE':
        return 'image/jpeg';
      case 'VIDEO':
        return 'video/mp4';
      case 'DOCUMENT':
        return 'application/pdf';
    }
  }

  private createAttachmentId(): string {
    const cryptoApi = typeof window !== 'undefined' ? window.crypto : undefined;
    if (cryptoApi?.randomUUID) {
      return cryptoApi.randomUUID();
    }
    if (cryptoApi?.getRandomValues) {
      const bytes = new Uint8Array(16);
      cryptoApi.getRandomValues(bytes);
      return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
    }
    this.attachmentIdCounter += 1;
    return `attachment-${Date.now()}-${this.attachmentIdCounter}`;
  }

  private updateAttachment(id: string, updater: (item: AttachmentItem) => AttachmentItem): void {
    const updated = this.attachmentItems.map((item) => {
      if (item.id !== id) {
        return item;
      }
      return updater(item);
    });
    this.attachments$.next(updated);
  }

  private async startUpload(itemId: string): Promise<void> {
    const item = this.attachmentItems.find(attachment => attachment.id === itemId);
    if (!item || !item.uploadId || !item.chunkSizeBytes || !item.attachmentId) {
      return;
    }

    const cancel$ = new Subject<void>();
    this.uploadCancelMap.set(itemId, cancel$);

    try {
      await this.uploadAttachmentChunks(itemId, item.uploadId, item.chunkSizeBytes, cancel$);
      if (cancel$.isStopped) {
        return;
      }
      if (!this.isAttachmentStillUploading(itemId)) {
        return;
      }
      this.updateAttachment(itemId, (existing) => ({ ...existing, status: 'FINALIZING', progress: 100 }));
      await firstValueFrom(this.messageService.finalizeAttachmentUpload(item.uploadId));
      this.updateAttachment(itemId, (existing) => ({ ...existing, status: 'COMPLETE', progress: 100 }));
      if (item.attachmentId) {
        this.uploadProgressByAttachmentId.set(item.attachmentId, 100);
      }
      setTimeout(() => {
        this.removeAttachment(itemId);
      }, 500);
    } catch (error) {
      if (!this.isAttachmentStillUploading(itemId)) {
        return;
      }
      if (error && (error as { message?: string }).message === 'cancelled') {
        return;
      }
      console.error('Upload failed', error);
      this.updateAttachment(itemId, (existing) => ({ ...existing, status: 'FAILED', error: 'Upload failed.' }));
    } finally {
      const activeCancel = this.uploadCancelMap.get(itemId);
      if (activeCancel && !activeCancel.isStopped) {
        activeCancel.complete();
      }
      this.uploadCancelMap.delete(itemId);
      const subscription = this.uploadSubscriptions.get(itemId);
      subscription?.unsubscribe();
      this.uploadSubscriptions.delete(itemId);
    }
  }

  private async uploadAttachmentChunks(
    itemId: string,
    uploadId: string,
    chunkSizeBytes: number,
    cancel$: Subject<void>
  ): Promise<void> {
    const item = this.attachmentItems.find(attachment => attachment.id === itemId);
    if (!item) {
      return;
    }
    const file = item.file;
    const totalChunks = Math.max(1, Math.ceil(file.size / chunkSizeBytes));
    let uploadedBytes = 0;

    for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex += 1) {
      if (cancel$.isStopped) {
        throw new Error('cancelled');
      }
      const start = chunkIndex * chunkSizeBytes;
      const end = Math.min(file.size, start + chunkSizeBytes);
      const chunk = file.slice(start, end);

      await new Promise<void>((resolve, reject) => {
        let settled = false;
        let cancelSubscription: Subscription | null = null;
        const subscription = this.messageService.uploadAttachmentChunk(uploadId, chunk, chunkIndex, totalChunks)
          .subscribe({
            next: (event) => {
              if (event.type === HttpEventType.UploadProgress) {
                const loaded = event.loaded || 0;
                const progress = Math.min(100, Math.round(((uploadedBytes + loaded) / file.size) * 100));
                this.updateAttachment(itemId, (existing) => ({ ...existing, progress }));
                if (item.attachmentId) {
                  this.uploadProgressByAttachmentId.set(item.attachmentId, progress);
                }
              }
            },
            error: (err) => {
              if (settled) {
                return;
              }
              settled = true;
              cancelSubscription?.unsubscribe();
              this.uploadSubscriptions.delete(itemId);
              reject(err);
            },
            complete: () => {
              if (settled) {
                return;
              }
              settled = true;
              cancelSubscription?.unsubscribe();
              this.uploadSubscriptions.delete(itemId);
              resolve();
            }
          });
        this.uploadSubscriptions.set(itemId, subscription);
        cancelSubscription = cancel$.subscribe(() => {
          if (settled) {
            return;
          }
          settled = true;
          subscription.unsubscribe();
          this.uploadSubscriptions.delete(itemId);
          cancelSubscription?.unsubscribe();
          reject(new Error('cancelled'));
        });
      });
      uploadedBytes += chunk.size;
    }
  }

  private isAttachmentStillUploading(itemId: string): boolean {
    const item = this.attachmentItems.find(attachment => attachment.id === itemId);
    if (!item) {
      return false;
    }
    return item.status === 'UPLOADING' || item.status === 'FINALIZING';
  }

  private ensureConversationNavigation(message: Message): void {
    if (!this.selectedConversationId || this.selectedConversationId !== message.conversationId) {
      this.recipientControl.setValue('');
      this.router.navigate(['/messages', message.conversationId]);
    } else {
      this.scrollToBottom();
    }
  }

  private syncUploadProgress(message: Message): void {
    if (!message.attachments || message.attachments.length === 0) {
      return;
    }
    message.attachments.forEach((attachment) => {
      if (attachment.status !== 'UPLOADING') {
        this.uploadProgressByAttachmentId.delete(attachment.id);
        const itemId = this.uploadItemByAttachmentId.get(attachment.id);
        if (itemId) {
          this.uploadItemByAttachmentId.delete(attachment.id);
        }
      }
    });
  }
}
