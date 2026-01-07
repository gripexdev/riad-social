import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class PostDialogService {
  private readonly activeDeletePostIdSubject = new BehaviorSubject<number | null>(null);
  readonly activeDeletePostId$ = this.activeDeletePostIdSubject.asObservable();

  openDelete(postId: number): void {
    this.activeDeletePostIdSubject.next(postId);
  }

  closeDelete(): void {
    this.activeDeletePostIdSubject.next(null);
  }
}
