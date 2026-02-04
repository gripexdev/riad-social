import { TestBed } from '@angular/core/testing';
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

  it('connects when token is available', () => {
    const activateSpy = spyOn(Client.prototype, 'activate').and.callFake(() => {});
    const authService = TestBed.inject(AuthService) as any;
    authService.getToken = () => 'token';
    (window as any).SockJS = function SockJSMock() {};

    service.connect();

    expect(activateSpy).toHaveBeenCalled();
  });
});
