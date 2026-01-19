import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should request notifications', () => {
    service.getNotifications().subscribe((response) => {
      expect(response.length).toBe(1);
    });

    const req = httpMock.expectOne('http://localhost:8080/api/notifications');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 1,
        type: 'FOLLOW',
        actorUsername: 'alice',
        createdAt: new Date().toISOString(),
        isRead: false,
        actorFollowed: false
      }
    ]);
  });

  it('should request unread count', () => {
    service.getUnreadCount().subscribe((response) => {
      expect(response.count).toBe(2);
    });

    const req = httpMock.expectOne('http://localhost:8080/api/notifications/unread-count');
    expect(req.request.method).toBe('GET');
    req.flush({ count: 2 });
  });

  it('should mark all read', () => {
    service.markAllRead().subscribe(() => {
      expect(true).toBeTrue();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/notifications/read');
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });
});
