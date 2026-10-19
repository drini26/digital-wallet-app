import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Balance, PayRequest, PaymentResponse, TransactionItem } from './models';

/** Talks to ledger-service for the customer: balance, history and payments. */
@Injectable({ providedIn: 'root' })
export class WalletService {
  private readonly http = inject(HttpClient);

  balance(): Observable<Balance> {
    return this.http.get<Balance>('/api/v1/wallet/balance');
  }

  history(limit = 20): Observable<TransactionItem[]> {
    return this.http.get<TransactionItem[]>('/api/v1/wallet/transactions', { params: { limit } });
  }

  pay(request: PayRequest, idempotencyKey: string): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>('/api/v1/transactions/pay', request,
      { headers: { 'Idempotency-Key': idempotencyKey } });
  }
}
