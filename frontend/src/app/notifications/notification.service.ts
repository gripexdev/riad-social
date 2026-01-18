import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type NotificationType = 'FOLLOW' | 'LIKE' | 'COMMENT';

export interface AppNotification {
  id: number;
  type: NotificationType;
  actorUsername: string;
  actorProfilePictureUrl?: string | null;
  postId?: number | null;
  postImageUrl?: string | null;
  commentPreview?: string | null;
  createdAt: string;
  isRead: boolean;
  actorFollowed: boolean;
}

export interface NotificationCountResponse {
  count: number;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private apiUrl = 'http://localhost:8080/api/notifications';

  constructor(private http: HttpClient) {}

  getNotifications(): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>(this.apiUrl);
  }

  getUnreadCount(): Observable<NotificationCountResponse> {
    return this.http.get<NotificationCountResponse>(`${this.apiUrl}/unread-count`);
  }

  markAllRead(): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/read`, {});
  }

  markRead(notificationId: number): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/${notificationId}/read`, {});
  }
}
