import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { NgZone } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { MessageRealtimeService } from './message-realtime.service';
import { AuthService } from '../auth/auth.service';

describe('MessageRealtimeService', () => {
  let service: MessageRealtimeService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { getToken: () => null } }
      ]
    });
    service = TestBed.inject(MessageRealtimeService);
  });

  it('does not connect without token', () => {
    service.connect();
    expect((service as any).client).toBeNull();
  });

  it('builds socket url', () => {
    const url = (service as any).buildSocketUrl('token');
    expect(url).toContain('ws?token=');
  });

  it('emits parsed message payload', (done) => {
    service.onMessage().subscribe((message) => {
      expect(message.id).toBe(1);
      done();
    });
    (service as any).handleMessage({ body: JSON.stringify({ id: 1 }) } as any);
  });

  it('emits parsed typing payload', (done) => {
    service.onTyping().subscribe((event) => {
      expect(event.conversationId).toBe(2);
      done();
    });
    (service as any).handleTyping({ body: JSON.stringify({ conversationId: 2, senderUsername: 'bob', typing: true }) } as any);
  });

  it('handles invalid payloads', () => {
    spyOn(console, 'error');
    (service as any).handleMessage({ body: '{invalid' } as any);
    (service as any).handleTyping({ body: '{invalid' } as any);
    expect(console.error).toHaveBeenCalled();
  });

  it('sendTyping publishes when active', () => {
    const publishSpy = jasmine.createSpy('publish');
    (service as any).client = { active: true, publish: publishSpy };
    service.sendTyping(3, true);
    expect(publishSpy).toHaveBeenCalled();
  });

  it('sendTyping no-ops when inactive', () => {
    const publishSpy = jasmine.createSpy('publish');
    (service as any).client = { active: false, publish: publishSpy };
    service.sendTyping(3, true);
    expect(publishSpy).not.toHaveBeenCalled();
  });

  it('disconnects active client', () => {
    const deactivateSpy = jasmine.createSpy('deactivate');
    (service as any).client = { active: true, deactivate: deactivateSpy };
    service.disconnect();
    expect(deactivateSpy).toHaveBeenCalled();
    expect((service as any).client).toBeNull();
  });

  it('disconnect no-ops when client is null', () => {
    (service as any).client = null;
    service.disconnect();
    expect((service as any).client).toBeNull();
  });

  it('skips connect when already active', () => {
    (service as any).client = { active: true };
    const activateSpy = spyOn(Client.prototype, 'activate');
    service.connect();
    expect(activateSpy).not.toHaveBeenCalled();
  });

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
    expect(subscribeSpy).toHaveBeenCalledWith('/user/queue/messages', jasmine.any(Function));
    expect(subscribeSpy).toHaveBeenCalledWith('/user/queue/typing', jasmine.any(Function));

    client.onStompError?.({} as any);
    client.onWebSocketError?.({} as any);
    client.onWebSocketClose?.({} as any);

    expect(consoleErrorSpy).toHaveBeenCalled();
    expect(consoleWarnSpy).toHaveBeenCalled();
  }));
});
