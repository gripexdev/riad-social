import { Injectable, NgZone } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject, Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Message } from './message.service';

export interface TypingEvent {
  conversationId: number;
  senderUsername: string;
  typing: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class MessageRealtimeService {
  private client: Client | null = null;
  private readonly messageSubject = new Subject<Message>();
  private readonly typingSubject = new Subject<TypingEvent>();

  constructor(
    private authService: AuthService,
    private zone: NgZone
  ) {}

  connect(): void {
    if (this.client?.active) {
      return;
    }
    const token = this.authService.getToken();
    if (!token) {
      return;
    }

    const socketUrl = this.buildSocketUrl(token);
    this.client = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
        token
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000
    });

    this.client.onConnect = () => {
      this.client?.subscribe('/user/queue/messages', (message) => {
        this.handleMessage(message);
      });
      this.client?.subscribe('/user/queue/typing', (message) => {
        this.handleTyping(message);
      });
    };

    this.client.onStompError = (frame) => {
      console.error('WebSocket STOMP error', frame);
    };

    this.client.onWebSocketError = (event) => {
      console.error('WebSocket error', event);
    };
    this.client.onWebSocketClose = (event) => {
      console.warn('WebSocket closed', event);
    };

    this.client.activate();
  }

  disconnect(): void {
    if (!this.client) {
      return;
    }
    this.client.deactivate();
    this.client = null;
  }

  onMessage(): Observable<Message> {
    return this.messageSubject.asObservable();
  }

  onTyping(): Observable<TypingEvent> {
    return this.typingSubject.asObservable();
  }

  sendTyping(conversationId: number, typing: boolean): void {
    if (!this.client?.active) {
      return;
    }
    this.client.publish({
      destination: '/app/messages/typing',
      body: JSON.stringify({ conversationId, typing })
    });
  }

  private handleMessage(message: IMessage): void {
    this.zone.run(() => {
      try {
        const payload = JSON.parse(message.body) as Message;
        this.messageSubject.next(payload);
      } catch (error) {
        console.error('Failed to parse realtime message payload', error);
      }
    });
  }

  private handleTyping(message: IMessage): void {
    this.zone.run(() => {
      try {
        const payload = JSON.parse(message.body) as TypingEvent;
        this.typingSubject.next(payload);
      } catch (error) {
        console.error('Failed to parse realtime typing payload', error);
      }
    });
  }

  private buildSocketUrl(token: string): string {
    if (typeof window === 'undefined') {
      return `http://localhost:8080/ws?token=${encodeURIComponent(token)}`;
    }
    const protocol = window.location.protocol === 'https:' ? 'https' : 'http';
    const host = window.location.hostname || 'localhost';
    return `${protocol}://${host}:8080/ws?token=${encodeURIComponent(token)}`;
  }

}
