import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
  linkValid = true;
  private resetToken = '';

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService
  ) {
    this.resetForm = this.fb.group({
      newPassword: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordMatchValidator });

    this.route.queryParamMap.subscribe((params) => {
      const tokenFromQuery = params.get('token') ?? '';
      this.resetToken = tokenFromQuery.trim();
      this.linkValid = this.resetToken.length > 0;
      if (this.linkValid) {
        this.resetForm.enable({ emitEvent: false });
      } else {
        this.resetForm.disable({ emitEvent: false });
      }
    });
  }

  reset(): void {
    if (!this.linkValid) {
      return;
    }

    if (this.resetForm.valid) {
      this.errorMessage = null;
      this.successMessage = null;

      const newPassword = this.resetForm.value.newPassword;

      this.authService.resetPassword(this.resetToken, newPassword).subscribe({
        next: (response) => {
          this.successMessage = response.message;
          this.resetForm.disable({ emitEvent: false });
          setTimeout(() => {
            this.router.navigate(['/login']);
          }, 1500);
        },
        error: (error) => {
          this.errorMessage = 'Failed to reset password. Please request a new link.';
          console.error('Reset password failed', error);
        }
      });
    } else {
      this.resetForm.markAllAsTouched();
    }
  }

  private passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
    const newPassword = control.get('newPassword')?.value;
    const confirmPassword = control.get('confirmPassword')?.value;
    if (!newPassword || !confirmPassword) {
      return null;
    }
    return newPassword === confirmPassword ? null : { passwordMismatch: true };
  }
}
