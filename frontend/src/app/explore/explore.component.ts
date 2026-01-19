import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Post, PostService } from '../post/post.service';
import { PostCardComponent } from '../post/post-card/post-card.component';

@Component({
  selector: 'app-explore',
  standalone: true,
  imports: [CommonModule, PostCardComponent, RouterLink],
  templateUrl: './explore.component.html',
  styleUrl: './explore.component.scss'
})
export class ExploreComponent implements OnInit {
  posts: Post[] = [];

  constructor(private postService: PostService) {}

  ngOnInit(): void {
    this.postService.getExplorePosts().subscribe(posts => {
      this.posts = posts;
    });
  }
}
