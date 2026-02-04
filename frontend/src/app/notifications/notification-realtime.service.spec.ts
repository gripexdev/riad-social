import { TestBed } from '@angular/core/testing';
import { NgZone } from '@angular/core';
import { NotificationRealtimeService } from './notification-realtime.service';
import { AuthService } from '../auth/auth.service';

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

  it('handles numeric count payload', (done) => {
    const message = { body: '5' } as any;
    service.onCount().subscribe((count) => {
      expect(count).toBe(5);
      done();
    });
    (service as any).handleCount(message);
  });

  it('builds socket url', () => {
    const url = (service as any).buildSocketUrl('token');
    expect(url).toContain('ws?token=');
  });
});
