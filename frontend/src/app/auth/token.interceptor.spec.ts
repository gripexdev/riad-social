import { TestBed } from '@angular/core/testing';
import { HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';

import { tokenInterceptor } from './token.interceptor';

describe('tokenInterceptor', () => {
  const interceptor: HttpInterceptorFn = (req, next) => 
    TestBed.runInInjectionContext(() => tokenInterceptor(req, next));

  beforeEach(() => {
    TestBed.configureTestingModule({});
    localStorage.clear();
  });

  it('should be created', () => {
    expect(interceptor).toBeTruthy();
  });

  it('adds Authorization header when token exists', () => {
    localStorage.setItem('jwt_token', 'token');
    const req = new HttpRequest('GET', '/api/test');
    const handler: HttpHandlerFn = (request) => {
      expect(request.headers.get('Authorization')).toBe('Bearer token');
      return { subscribe: () => {} } as any;
    };
    interceptor(req, handler);
  });
});
