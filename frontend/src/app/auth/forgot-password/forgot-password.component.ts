import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink
  ],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss'
})
export class ForgotPasswordComponent {
  forgotForm: FormGroup;
  errorMessage: string | null = null;
  successMessage: string | null = null;
  resetToken: string | null = null;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService
  ) {
    this.forgotForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]]
    });
  }

  submit(): void {
    if (this.forgotForm.valid) {
      this.errorMessage = null;
      this.successMessage = null;
      this.resetToken = null;

      const email = this.forgotForm.value.email;
      this.authService.forgotPassword(email).subscribe({
        next: (response) => {
          this.successMessage = response.message;
          this.resetToken = response.resetToken ?? null;
        },
        error: (error) => {
          this.errorMessage = 'Failed to request password reset. Please try again.';
          console.error('Forgot password failed', error);
        }
      });
    }
  }
}
