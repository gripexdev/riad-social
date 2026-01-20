import { Injectable, NgZone } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject, Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Message } from './message.service';

@Injectable({
  providedIn: 'root'
})
export class MessageRealtimeService {
  private client: Client | null = null;
  private readonly messageSubject = new Subject<Message>();

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

    this.client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000
    });

    this.client.onConnect = () => {
      this.client?.subscribe('/user/queue/messages', (message) => {
        this.handleMessage(message);
      });
    };

    this.client.onStompError = (frame) => {
      console.error('WebSocket STOMP error', frame);
    };

    this.client.onWebSocketError = (event) => {
      console.error('WebSocket error', event);
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
}
