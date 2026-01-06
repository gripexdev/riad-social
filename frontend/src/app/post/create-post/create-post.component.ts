import { Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { PostService } from '../post.service';

@Component({
  selector: 'app-create-post',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule
  ],
  templateUrl: './create-post.component.html',
  styleUrl: './create-post.component.scss'
})
export class CreatePostComponent implements OnDestroy {
  createPostForm: FormGroup;
  selectedFile: File | null = null;
  previewUrl: string | null = null;
  errorMessage: string | null = null;
  private readonly maxFileSizeBytes = 10 * 1024 * 1024;

  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;

  constructor(
    private fb: FormBuilder,
    private postService: PostService,
    private router: Router
  ) {
    this.createPostForm = this.fb.group({
      caption: ['', Validators.maxLength(2200)], // Instagram caption limit
      file: [null, Validators.required]
    });
  }

  onFileSelected(event: Event): void {
    const element = event.currentTarget as HTMLInputElement;
    let fileList: FileList | null = element.files;
    this.errorMessage = null;
    if (fileList && fileList.length > 0) {
      const file = fileList[0];
      if (!file.type.startsWith('image/')) {
        this.clearSelectedFile();
        this.errorMessage = 'Please choose an image file.';
        return;
      }
      if (file.size > this.maxFileSizeBytes) {
        this.clearSelectedFile();
        this.errorMessage = 'Image is too large. Max size is 10MB.';
        return;
      }
      this.selectedFile = file;
      this.createPostForm.patchValue({ file: this.selectedFile });
      this.setPreviewUrl(file);
    }
  }

  onCreatePost(): void {
    this.errorMessage = null;
    if (this.createPostForm.valid && this.selectedFile) {
      this.postService.createPost(this.selectedFile, this.createPostForm.value.caption).subscribe({
        next: (response) => {
          console.log('Post created successfully', response);
          this.router.navigate(['/home']);
        },
        error: (error) => {
          this.errorMessage = 'Failed to create post. Please try again.';
          console.error('Create post failed', error);
        }
      });
    } else {
      this.errorMessage = 'Please select an image and provide a caption.';
    }
  }

  get captionPreview(): string {
    return (this.createPostForm.get('caption')?.value || '').trim();
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    const kb = bytes / 1024;
    if (kb < 1024) {
      return `${kb.toFixed(1)} KB`;
    }
    const mb = kb / 1024;
    return `${mb.toFixed(1)} MB`;
  }

  clearSelectedFile(): void {
    this.selectedFile = null;
    this.createPostForm.patchValue({ file: null });
    if (this.fileInput?.nativeElement) {
      this.fileInput.nativeElement.value = '';
    }
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
      this.previewUrl = null;
    }
  }

  ngOnDestroy(): void {
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
    }
  }

  private setPreviewUrl(file: File): void {
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
    }
    this.previewUrl = URL.createObjectURL(file);
  }
}
