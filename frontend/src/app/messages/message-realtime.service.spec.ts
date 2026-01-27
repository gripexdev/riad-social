import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { MessageRealtimeService } from './message-realtime.service';
import { AuthService } from '../auth/auth.service';
import { Client } from '@stomp/stompjs';

describe('MessageRealtimeService', () => {
  let service: MessageRealtimeService;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['getToken']);
    TestBed.configureTestingModule({
      providers: [
        MessageRealtimeService,
        { provide: AuthService, useValue: authServiceSpy }
      ]
    });
    service = TestBed.inject(MessageRealtimeService);
  });

  it('does not connect without token', () => {
    authServiceSpy.getToken.and.returnValue(null);
    service.connect();
    expect((service as any).client).toBeNull();
  });

  it('returns early when already active', () => {
    authServiceSpy.getToken.and.returnValue('token');
    (service as any).client = { active: true } as any;

    service.connect();

    expect(authServiceSpy.getToken).not.toHaveBeenCalled();
  });

  it('bails out if client becomes active during connect', fakeAsync(() => {
    authServiceSpy.getToken.and.returnValue('token');
    (window as any).SockJS = function SockJS() { return {}; };
    const activateSpy = spyOn(Client.prototype, 'activate').and.stub();

    service.connect();
    (service as any).client = { active: true } as any;
    flushMicrotasks();

    expect(activateSpy).not.toHaveBeenCalled();
  }));

  it('connects with token and activates client', fakeAsync(() => {
    authServiceSpy.getToken.and.returnValue('token');
    (window as any).SockJS = function SockJS() { return {}; };
    const activateSpy = spyOn(Client.prototype, 'activate').and.stub();

    service.connect();
    flushMicrotasks();

    expect(activateSpy).toHaveBeenCalled();
  }));

  it('subscribes to topics and logs websocket errors', fakeAsync(() => {
    authServiceSpy.getToken.and.returnValue('token');
    (window as any).SockJS = function SockJS() { return {}; };
    spyOn(Client.prototype, 'activate').and.stub();
    const subscribeSpy = spyOn(Client.prototype, 'subscribe').and.stub();
    const errorSpy = spyOn(console, 'error');
    const warnSpy = spyOn(console, 'warn');

    service.connect();
    flushMicrotasks();

    const client = (service as any).client as Client;
    client.onConnect?.({} as any);
    expect(subscribeSpy).toHaveBeenCalledWith('/user/queue/messages', jasmine.any(Function));
    expect(subscribeSpy).toHaveBeenCalledWith('/user/queue/typing', jasmine.any(Function));

    client.onStompError?.({} as any);
    client.onWebSocketError?.({} as any);
    client.onWebSocketClose?.({} as any);

    expect(errorSpy).toHaveBeenCalled();
    expect(warnSpy).toHaveBeenCalled();
  }));

  it('disconnect deactivates the client', () => {
    const deactivateSpy = jasmine.createSpy('deactivate');
    (service as any).client = { deactivate: deactivateSpy } as any;

    service.disconnect();

    expect(deactivateSpy).toHaveBeenCalled();
    expect((service as any).client).toBeNull();
  });

  it('publishes typing events when connected', () => {
    const publishSpy = jasmine.createSpy('publish');
    (service as any).client = { active: true, publish: publishSpy } as any;

    service.sendTyping(4, true);

    expect(publishSpy).toHaveBeenCalledWith(jasmine.objectContaining({
      destination: '/app/messages/typing'
    }));
  });

  it('does not publish typing events when inactive', () => {
    const publishSpy = jasmine.createSpy('publish');
    (service as any).client = { active: false, publish: publishSpy } as any;

    service.sendTyping(4, true);

    expect(publishSpy).not.toHaveBeenCalled();
  });

  it('parses incoming messages', (done) => {
    service.onMessage().subscribe((msg) => {
      expect(msg.content).toBe('hi');
      done();
    });

    (service as any).handleMessage({ body: JSON.stringify({ content: 'hi' }) });
  });

  it('parses typing events', (done) => {
    service.onTyping().subscribe((event) => {
      expect(event.typing).toBeTrue();
      done();
    });

    (service as any).handleTyping({ body: JSON.stringify({ conversationId: 1, senderUsername: 'bob', typing: true }) });
  });

  it('handles invalid payloads gracefully', () => {
    const errorSpy = spyOn(console, 'error');

    (service as any).handleMessage({ body: '{bad' });
    (service as any).handleTyping({ body: '{bad' });

    expect(errorSpy).toHaveBeenCalled();
  });
});
