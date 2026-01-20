import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Conversation {
  id: number;
  participantUsername: string;
  participantFullName?: string | null;
  participantProfilePictureUrl?: string | null;
  lastMessagePreview?: string | null;
  lastMessageAt?: string | null;
  lastMessageSenderUsername?: string | null;
  unreadCount: number;
}

export interface Message {
  id: number;
  conversationId: number;
  senderUsername: string;
  recipientUsername: string;
  content: string;
  createdAt: string;
  isRead: boolean;
  readAt?: string | null;
}

export interface SendMessageRequest {
  recipientUsername: string;
  content: string;
}

@Injectable({
  providedIn: 'root'
})
export class MessageService {
  private apiUrl = 'http://localhost:8080/api/messages';

  constructor(private http: HttpClient) {}

  getConversations(): Observable<Conversation[]> {
    return this.http.get<Conversation[]>(`${this.apiUrl}/conversations`);
  }

  getMessages(conversationId: number): Observable<Message[]> {
    return this.http.get<Message[]>(`${this.apiUrl}/conversations/${conversationId}`);
  }

  markConversationRead(conversationId: number): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/conversations/${conversationId}/read`, {});
  }

  sendMessage(request: SendMessageRequest): Observable<Message> {
    return this.http.post<Message>(this.apiUrl, request);
  }
}
