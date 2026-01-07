import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink
  ],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss'
})
export class ResetPasswordComponent {
  resetForm: FormGroup;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {
    const tokenFromQuery = this.route.snapshot.queryParamMap.get('token') ?? '';
    this.resetForm = this.fb.group({
      token: [tokenFromQuery, [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  reset(): void {
    if (this.resetForm.valid) {
      this.errorMessage = null;
      this.successMessage = null;

      const token = this.resetForm.value.token;
      const newPassword = this.resetForm.value.newPassword;

      this.authService.resetPassword(token, newPassword).subscribe({
        next: (response) => {
          this.successMessage = response.message;
        },
        error: (error) => {
          this.errorMessage = 'Failed to reset password. Please check your token.';
          console.error('Reset password failed', error);
        }
      });
    }
  }
}
