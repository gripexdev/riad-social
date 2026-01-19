import { Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { ForgotPasswordComponent } from './auth/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './auth/reset-password/reset-password.component';
import { authGuard } from './auth/auth.guard';
import { UserSearchComponent } from './search/user-search.component';
import { CreatePostComponent } from './post/create-post/create-post.component';
import { ProfileComponent } from './profile/profile/profile.component';
import { ExploreComponent } from './explore/explore.component';

export const routes: Routes = [
    { path: '', redirectTo: '/home', pathMatch: 'full' },
    { path: 'home', component: HomeComponent, canActivate: [authGuard] },
    { path: 'explore', component: ExploreComponent, canActivate: [authGuard] },
    { path: 'login', component: LoginComponent },
    { path: 'register', component: RegisterComponent },
    { path: 'forgot-password', component: ForgotPasswordComponent },
    { path: 'reset-password', component: ResetPasswordComponent },
    { path: 'search', component: UserSearchComponent, canActivate: [authGuard] },
    { path: 'create-post', component: CreatePostComponent, canActivate: [authGuard] },
    { path: 'users/:username', component: ProfileComponent, canActivate: [authGuard] }
];
