import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type AttachmentStatus = 'UPLOADING' | 'READY' | 'FAILED' | 'QUARANTINED' | 'EXPIRED';
export type AttachmentType = 'IMAGE' | 'VIDEO' | 'DOCUMENT';

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
  content?: string | null;
  attachments?: MessageAttachment[];
  createdAt: string;
  isRead: boolean;
  readAt?: string | null;
}

export interface SendMessageRequest {
  recipientUsername: string;
  content: string;
}

export interface MessageAttachment {
  id: number;
  type: AttachmentType;
  mimeType?: string | null;
  sizeBytes: number;
  checksum?: string | null;
  width?: number | null;
  height?: number | null;
  durationSeconds?: number | null;
  altText?: string | null;
  url?: string | null;
  thumbnailUrl?: string | null;
  status: AttachmentStatus;
  expiresAt?: string | null;
  originalFilename?: string | null;
}

export interface AttachmentUploadRequest {
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  checksum?: string | null;
  width?: number | null;
  height?: number | null;
  durationSeconds?: number | null;
  altText?: string | null;
}

export interface CreateAttachmentUploadSessionRequest {
  recipientUsername: string;
  content?: string | null;
  expiresInSeconds?: number | null;
  attachments: AttachmentUploadRequest[];
}

export interface AttachmentUploadSessionResponse {
  uploadId: string;
  attachmentId: number;
  uploadUrl: string;
  finalizeUrl: string;
  chunkSizeBytes: number;
}

export interface CreateAttachmentUploadSessionResponse {
  message: Message;
  uploads: AttachmentUploadSessionResponse[];
}

export interface UploadChunkResponse {
  uploadId: string;
  uploadedChunks: number;
  totalChunks: number;
}

@Injectable({
  providedIn: 'root'
})
export class MessageService {
  private apiUrl = 'http://localhost:8080/api/messages';
  private attachmentsUrl = 'http://localhost:8080/api/messages/attachments';

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

  createAttachmentUploadSessions(request: CreateAttachmentUploadSessionRequest): Observable<CreateAttachmentUploadSessionResponse> {
    return this.http.post<CreateAttachmentUploadSessionResponse>(`${this.attachmentsUrl}/sessions`, request);
  }

  uploadAttachmentChunk(
    uploadId: string,
    file: Blob,
    chunkIndex: number,
    totalChunks: number
  ): Observable<HttpEvent<UploadChunkResponse>> {
    const formData = new FormData();
    formData.append('file', file);
    let params = new HttpParams();
    params = params.set('chunkIndex', String(chunkIndex));
    params = params.set('totalChunks', String(totalChunks));
    return this.http.post<UploadChunkResponse>(`${this.attachmentsUrl}/uploads/${uploadId}`, formData, {
      params,
      reportProgress: true,
      observe: 'events'
    });
  }

  finalizeAttachmentUpload(uploadId: string): Observable<MessageAttachment> {
    return this.http.post<MessageAttachment>(`${this.attachmentsUrl}/uploads/${uploadId}/finalize`, {});
  }

  cancelAttachmentUpload(uploadId: string): Observable<void> {
    return this.http.delete<void>(`${this.attachmentsUrl}/uploads/${uploadId}`);
  }
}
