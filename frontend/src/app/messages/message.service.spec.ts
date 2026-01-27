import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpEventType } from '@angular/common/http';
import { MessageService } from './message.service';

describe('MessageService', () => {
  let service: MessageService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MessageService]
    });
    service = TestBed.inject(MessageService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('fetches conversations', () => {
    service.getConversations().subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/messages/conversations');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('fetches messages for conversation', () => {
    service.getMessages(12).subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/messages/conversations/12');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('marks a conversation read', () => {
    service.markConversationRead(8).subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/messages/conversations/8/read');
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('sends a text message', () => {
    service.sendMessage({ recipientUsername: 'bob', content: 'hi' }).subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/messages');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('creates attachment upload sessions', () => {
    service.createAttachmentUploadSessions({ recipientUsername: 'bob', attachments: [] }).subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/messages/attachments/sessions');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('uploads attachment chunks with progress events', () => {
    const file = new Blob(['data'], { type: 'text/plain' });
    service.uploadAttachmentChunk('upload1', file, 0, 1).subscribe((event) => {
      if (event.type === HttpEventType.Response) {
        expect((event as any).body).toEqual({ ok: true });
      }
    });
    const req = httpMock.expectOne((req) => req.url === 'http://localhost:8080/api/messages/attachments/uploads/upload1');
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('chunkIndex')).toBe('0');
    expect(req.request.params.get('totalChunks')).toBe('1');
    req.event({ type: HttpEventType.UploadProgress, loaded: 1, total: 4 } as any);
    req.flush({ ok: true });
  });

  it('finalizes attachment upload', () => {
    service.finalizeAttachmentUpload('upload2').subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/messages/attachments/uploads/upload2/finalize');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('cancels attachment upload', () => {
    service.cancelAttachmentUpload('upload3').subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/messages/attachments/uploads/upload3');
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });
});
