import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Post } from '../post/post.service';

export interface Profile {
  username: string;
  fullName: string;
  bio: string;
  profilePictureUrl: string;
  postCount: number;
  followerCount: number;
  followingCount: number;
  posts: Post[];
  isFollowing: boolean;
}

export interface UserSearchResult {
  username: string;
  fullName: string;
  profilePictureUrl: string;
  isFollowing: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class ProfileService {

  private apiUrl = 'http://localhost:8080/api/users';

  constructor(private http: HttpClient) { }

  getProfile(username: string): Observable<Profile> {
    return this.http.get<Profile>(`${this.apiUrl}/${username}`);
  }

  followUser(username: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${username}/follow`, {});
  }

  unfollowUser(username: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${username}/unfollow`, {});
  }

  updateProfile(bio: string, avatar?: File | null): Observable<Profile> {
    const formData = new FormData();
    if (bio !== undefined && bio !== null) {
      formData.append('bio', bio);
    }
    if (avatar) {
      formData.append('avatar', avatar);
    }
    return this.http.put<Profile>(`${this.apiUrl}/me`, formData);
  }

  // Search users by username or full name for discovery.
  searchUsers(query: string, limit: number = 20): Observable<UserSearchResult[]> {
    return this.http.get<UserSearchResult[]>(`${this.apiUrl}/search`, {
      params: {
        q: query,
        limit
      }
    });
  }

  getMentionSuggestions(limit: number = 6): Observable<UserSearchResult[]> {
    return this.http.get<UserSearchResult[]>(`${this.apiUrl}/mentions`, {
      params: {
        limit
      }
    });
  }
}
