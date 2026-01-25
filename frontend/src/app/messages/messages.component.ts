import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AuthService } from '../auth/auth.service';
import { Conversation, Message, MessageService } from './message.service';
import { MessageRealtimeService } from './message-realtime.service';

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
  private readonly destroy$ = new Subject<void>();

  @ViewChild('messageScroll') messageScroll?: ElementRef<HTMLDivElement>;

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

  ngOnInit(): void {
    this.loadConversations();
    this.messageRealtimeService.connect();
    this.messageRealtimeService.onMessage()
      .pipe(takeUntil(this.destroy$))
      .subscribe(message => {
        this.handleIncomingMessage(message);
      });
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const rawId = params.get('conversationId');
      const parsedId = rawId ? Number(rawId) : Number.NaN;
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
  }

  loadConversations(): void {
    this.isLoadingConversations = true;
    this.errorMessage = null;
    this.messageService.getConversations().subscribe({
      next: (conversations) => {
        this.conversations = conversations || [];
        this.syncSelectedConversation();
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
    this.selectedConversationId = null;
    this.selectedConversation = null;
    this.messages = [];
    this.messageLoadError = null;
    this.sendError = null;
    this.isComposingNew = true;
    this.recipientControl.setValue('');
    this.messageControl.setValue('');
  }

  sendMessage(): void {
    if (this.isSending) {
      return;
    }
    const recipient = this.selectedConversation
      ? this.selectedConversation.participantUsername
      : this.recipientControl.value.trim();
    const content = this.messageControl.value.trim();

    if (!recipient) {
      this.sendError = 'Recipient username is required.';
      return;
    }
    if (!content) {
      this.sendError = 'Message content is required.';
      return;
    }
    if (content.length > this.maxMessageLength) {
      this.sendError = `Message must be under ${this.maxMessageLength} characters.`;
      return;
    }

    this.isSending = true;
    this.sendError = null;
    this.messageService.sendMessage({ recipientUsername: recipient, content }).subscribe({
      next: (message) => {
        this.isSending = false;
        this.messageControl.setValue('');
        if (!this.selectedConversationId || this.selectedConversationId !== message.conversationId) {
          this.recipientControl.setValue('');
          this.router.navigate(['/messages', message.conversationId]);
        } else {
          this.appendMessageIfMissing(message);
          this.scrollToBottom();
        }
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

  canSend(): boolean {
    const recipient = this.selectedConversation
      ? this.selectedConversation.participantUsername
      : this.recipientControl.value.trim();
    return !!recipient && this.messageControl.value.trim().length > 0 && !this.isSending;
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
    this.messageRealtimeService.disconnect();
    this.destroy$.next();
    this.destroy$.complete();
  }

  private handleIncomingMessage(message: Message): void {
    const isInActiveConversation = this.selectedConversationId === message.conversationId;
    if (isInActiveConversation) {
      this.appendMessageIfMissing(message);
      this.scrollToBottom();
      if (!this.isOutgoing(message)) {
        this.markConversationRead(message.conversationId);
      }
    }
    this.updateConversationPreview(message);
  }

  private appendMessageIfMissing(message: Message): void {
    if (this.messages.some(existing => existing.id === message.id)) {
      return;
    }
    this.messages = [...this.messages, message];
  }

  private updateConversationPreview(message: Message): void {
    const conversation = this.conversations.find(item => item.id === message.conversationId);
    if (!conversation) {
      this.loadConversations();
      return;
    }
    const isOutgoing = this.isOutgoing(message);
    conversation.lastMessagePreview = this.buildPreview(message.content);
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

  private buildPreview(content: string): string {
    const normalized = content.trim().replace(/\s+/g, ' ');
    if (normalized.length <= this.previewLimit) {
      return normalized;
    }
    return `${normalized.substring(0, this.previewLimit).trim()}...`;
  }
}
