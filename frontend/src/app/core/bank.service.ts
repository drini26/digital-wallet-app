import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateTransferRequest, Transfer } from './models';

/** Talks to banking-service for the customer: bank transfers. */
@Injectable({ providedIn: 'root' })
export class BankService {
  private readonly http = inject(HttpClient);

  createTransfer(request: CreateTransferRequest, idempotencyKey: string): Observable<Transfer> {
    return this.http.post<Transfer>('/api/v1/bank/transfer', request,
      { headers: { 'Idempotency-Key': idempotencyKey } });
  }

  transfers(): Observable<Transfer[]> {
    return this.http.get<Transfer[]>('/api/v1/bank/transfers');
  }
}
