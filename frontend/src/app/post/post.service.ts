import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
export interface CommentResponse {
  id: number;
  content: string;
  username: string;
  createdAt: string;
  parentId?: number | null;
  replies?: CommentResponse[];
}

export interface Post {
  id: number;
  imageUrl: string;
  caption: string;
  username: string; // The backend PostResponse has username directly
  profilePictureUrl?: string;
  createdAt: string;
  likesCount: number;
  likedByCurrentUser: boolean;
  comments: CommentResponse[];
}

@Injectable({
  providedIn: 'root'
})
export class PostService {

  private apiUrl = 'http://localhost:8080/api/posts';

  constructor(private http: HttpClient) { }

  getPosts(): Observable<Post[]> {
    return this.http.get<Post[]>(this.apiUrl);
  }

  getExplorePosts(): Observable<Post[]> {
    return this.http.get<Post[]>(`${this.apiUrl}/explore`);
  }

  createPost(file: File, caption: string): Observable<Post> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('caption', caption);
    return this.http.post<Post>(this.apiUrl, formData);
  }

  toggleLike(postId: number): Observable<Post> {
    return this.http.post<Post>(`${this.apiUrl}/${postId}/like`, {});
  }

  addComment(postId: number, content: string, parentCommentId?: number | null): Observable<CommentResponse> {
    const commentRequest = { content: content, parentCommentId: parentCommentId ?? null };
    return this.http.post<CommentResponse>(`${this.apiUrl}/${postId}/comment`, commentRequest);
  }

  updatePost(postId: number, caption: string): Observable<Post> {
    return this.http.put<Post>(`${this.apiUrl}/${postId}`, { caption });
  }

  deletePost(postId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${postId}`);
  }
}
