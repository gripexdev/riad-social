import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { NgZone } from '@angular/core';
import { NotificationRealtimeService } from './notification-realtime.service';
import { AuthService } from '../auth/auth.service';
import { Client } from '@stomp/stompjs';

describe('NotificationRealtimeService', () => {
  let service: NotificationRealtimeService;
  let zone: NgZone;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { getToken: () => null } }
      ]
    });
    service = TestBed.inject(NotificationRealtimeService);
    zone = TestBed.inject(NgZone);
  });

  it('does not connect without token', () => {
    spyOn(console, 'error');
    service.connect();
    expect(console.error).not.toHaveBeenCalled();
  });

  it('skips connect when already active', () => {
    (service as any).client = { active: true };
    service.connect();
    expect((service as any).client.active).toBeTrue();
  });

  it('handles numeric count payload', (done) => {
    const message = { body: '5' } as any;
    service.onCount().subscribe((count) => {
      expect(count).toBe(5);
      done();
    });
    (service as any).handleCount(message);
  });

  it('handles json count payload', (done) => {
    const message = { body: JSON.stringify({ count: 7 }) } as any;
    service.onCount().subscribe((count) => {
      expect(count).toBe(7);
      done();
    });
    (service as any).handleCount(message);
  });

  it('builds socket url', () => {
    const url = (service as any).buildSocketUrl('token');
    expect(url).toContain('ws?token=');
  });

  it('builds socket url using current location', () => {
    const url = (service as any).buildSocketUrl('token');
    expect(url).toContain('://');
    expect(url).toContain(':8080/ws?token=token');
  });

  it('disconnect no-ops without client', () => {
    (service as any).client = null;
    service.disconnect();
    expect((service as any).client).toBeNull();
  });

  it('connects when token is available', fakeAsync(() => {
    spyOn(Client.prototype, 'activate').and.callFake(() => {});
    const authService = TestBed.inject(AuthService) as any;
    authService.getToken = () => 'token';
    const originalSockJS = (window as any).SockJS;
    (window as any).SockJS = function SockJSMock() {};

    service.connect();

    flushMicrotasks();
    expect((service as any).client).toBeTruthy();
    (window as any).SockJS = originalSockJS;
  }));

  it('logs error when SockJS loader rejects', fakeAsync(() => {
    const consoleSpy = spyOn(console, 'error');
    const authService = TestBed.inject(AuthService) as any;
    authService.getToken = () => 'token';
    const originalSockJS = (window as any).SockJS;
    try {
      (window as any).SockJS = {
        then: (_resolve: any, reject: any) => reject(new Error('fail'))
      };

      service.connect();
      flushMicrotasks();

      expect(consoleSpy).toHaveBeenCalled();
    } finally {
      (window as any).SockJS = originalSockJS;
    }
  }));

  it('does not activate if client becomes active before loader resolves', fakeAsync(() => {
    const activateSpy = spyOn(Client.prototype, 'activate').and.callFake(() => {});
    const authService = TestBed.inject(AuthService) as any;
    authService.getToken = () => 'token';
    (window as any).SockJS = function SockJSMock() {};

    service.connect();
    (service as any).client = { active: true };

    flushMicrotasks();
    expect(activateSpy).not.toHaveBeenCalled();
  }));

  it('disconnects active client', () => {
    const deactivateSpy = jasmine.createSpy('deactivate');
    (service as any).client = { deactivate: deactivateSpy } as any;
    service.disconnect();
    expect(deactivateSpy).toHaveBeenCalled();
  });

  it('handles invalid count payload', () => {
    spyOn(console, 'error');
    const message = { body: '{invalid' } as any;
    (service as any).handleCount(message);
    expect(console.error).toHaveBeenCalled();
  });

  it('does not emit when count is missing or NaN', () => {
    let called = false;
    service.onCount().subscribe(() => {
      called = true;
    });
    (service as any).handleCount({ body: JSON.stringify({ count: 'nope' }) } as any);
    (service as any).handleCount({ body: 'not-a-number' } as any);
    expect(called).toBeFalse();
  });

  it('subscribes on connect and logs websocket errors', fakeAsync(() => {
    const activateSpy = spyOn(Client.prototype, 'activate').and.callFake(() => {});
    const subscribeSpy = spyOn(Client.prototype, 'subscribe').and.callFake(() => ({ unsubscribe() {} } as any));
    const consoleErrorSpy = spyOn(console, 'error');
    const consoleWarnSpy = spyOn(console, 'warn');
    const authService = TestBed.inject(AuthService) as any;
    authService.getToken = () => 'token';
    (window as any).SockJS = function SockJSMock() {};

    service.connect();
    flushMicrotasks();

    expect(activateSpy).toHaveBeenCalled();
    const client = (service as any).client as Client;
    client.onConnect?.({} as any);
    expect(subscribeSpy).toHaveBeenCalledWith('/user/queue/notification-count', jasmine.any(Function));

    client.onStompError?.({} as any);
    client.onWebSocketError?.({} as any);
    client.onWebSocketClose?.({} as any);
    expect(consoleErrorSpy).toHaveBeenCalled();
    expect(consoleWarnSpy).toHaveBeenCalled();
  }));
});
