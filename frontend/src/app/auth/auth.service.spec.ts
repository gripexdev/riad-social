import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('stores token on login', () => {
    service.login({ username: 'alice', password: 'pass' }).subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/auth/login');
    req.flush({ token: 'jwt-token' });

    expect(service.getToken()).toBe('jwt-token');
  });

  it('stores token on register', () => {
    service.register({ username: 'alice' }).subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/auth/register');
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'reg-token' });
    expect(service.getToken()).toBe('reg-token');
  });

  it('requests password reset endpoints', () => {
    service.forgotPassword('a@b.com').subscribe();
    const forgotReq = httpMock.expectOne('http://localhost:8080/api/auth/forgot-password');
    expect(forgotReq.request.method).toBe('POST');
    forgotReq.flush({ message: 'ok' });

    service.resetPassword('token', 'newpass').subscribe();
    const resetReq = httpMock.expectOne('http://localhost:8080/api/auth/reset-password');
    expect(resetReq.request.method).toBe('POST');
    resetReq.flush({ message: 'ok' });
  });

  it('decodes username from token', () => {
    const payload = btoa(JSON.stringify({ sub: 'alice' }));
    const token = `header.${payload}.sig`;
    localStorage.setItem('jwt_token', token);

    expect(service.getUsername()).toBe('alice');
  });

  it('returns null on invalid token', () => {
    localStorage.setItem('jwt_token', 'invalid.token');
    expect(service.getUsername()).toBeNull();
  });

  it('returns null when no token is stored', () => {
    expect(service.getUsername()).toBeNull();
  });

  it('reports authentication state and clears token', () => {
    localStorage.setItem('jwt_token', 'token');
    expect(service.isAuthenticated()).toBeTrue();
    service.removeToken();
    expect(service.isAuthenticated()).toBeFalse();
  });
});
