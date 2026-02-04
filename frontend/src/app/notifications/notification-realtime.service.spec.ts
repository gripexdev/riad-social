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
});
