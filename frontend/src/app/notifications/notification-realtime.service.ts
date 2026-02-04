import { Injectable, NgZone } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { NotificationCountResponse } from './notification.service';

@Injectable({
  providedIn: 'root'
})
export class NotificationRealtimeService {
  private client: Client | null = null;
  private readonly countSubject = new Subject<number>();

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
    const sockJsFromWindow = typeof window !== 'undefined' ? (window as any).SockJS : undefined;
    const sockJsLoader = sockJsFromWindow
      ? Promise.resolve(sockJsFromWindow)
      : import('sockjs-client').then(({ default: SockJS }) => SockJS);

    sockJsLoader
      .then((SockJS) => {
        if (this.client?.active) {
          return;
        }
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
          this.client?.subscribe('/user/queue/notification-count', (message) => {
            this.handleCount(message);
          });
        };

        this.client.onStompError = (frame) => {
          console.error('Notification WebSocket STOMP error', frame);
        };

        this.client.onWebSocketError = (event) => {
          console.error('Notification WebSocket error', event);
        };

        this.client.onWebSocketClose = (event) => {
          console.warn('Notification WebSocket closed', event);
        };

        this.client.activate();
      })
      .catch((error) => {
        console.error('Failed to load realtime transport', error);
      });
  }

  disconnect(): void {
    if (!this.client) {
      return;
    }
    this.client.deactivate();
    this.client = null;
  }

  onCount(): Observable<number> {
    return this.countSubject.asObservable();
  }

  private handleCount(message: IMessage): void {
    this.zone.run(() => {
      try {
        const payload = JSON.parse(message.body) as NotificationCountResponse;
        if (payload && typeof payload.count === 'number') {
          this.countSubject.next(payload.count);
          return;
        }
        const parsed = Number(message.body);
        if (!Number.isNaN(parsed)) {
          this.countSubject.next(parsed);
        }
      } catch (error) {
        console.error('Failed to parse notification count payload', error);
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
