import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdminMetrics, AdminTransaction, BatchResult, ErrorEvent, MockBankMode, MockBankStatus, Reconciliation, SweepResult, SystemStatus,
} from './models';

/** Talks to the manager endpoints (ledger and banking admin, mock bank controls). */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  metrics(): Observable<AdminMetrics> {
    return this.http.get<AdminMetrics>('/api/v1/admin/metrics');
  }

  errors(size = 20): Observable<ErrorEvent[]> {
    return this.http.get<ErrorEvent[]>('/api/v1/admin/errors', { params: { page: 0, size } });
  }

  systemStatus(): Observable<SystemStatus> {
    return this.http.get<SystemStatus>('/api/v1/admin/system/status');
  }

  unfreeze(): Observable<SystemStatus> {
    return this.http.post<SystemStatus>('/api/v1/admin/system/unfreeze', {});
  }

  reconcile(): Observable<Reconciliation> {
    return this.http.post<Reconciliation>('/api/v1/admin/ledger/reconcile', {});
  }

  reconciliations(): Observable<Reconciliation[]> {
    return this.http.get<Reconciliation[]>('/api/v1/admin/ledger/reconciliations');
  }

  sweep(): Observable<SweepResult> {
    return this.http.post<SweepResult>('/api/v1/admin/banking/sweep', {});
  }

  runBatch(): Observable<BatchResult> {
    return this.http.post<BatchResult>('/api/v1/admin/banking/batches/run', {});
  }

  transactions(): Observable<AdminTransaction[]> {
    return this.http.get<AdminTransaction[]>('/api/v1/admin/transactions');
  }

  reverse(transactionId: string, reason: string): Observable<unknown> {
    return this.http.post(`/api/v1/admin/transactions/${transactionId}/reverse`, { reason });
  }

  mockBank(): Observable<MockBankStatus> {
    return this.http.get<MockBankStatus>('/api/v1/admin/mock-bank/settings');
  }

  setMockBankMode(mode: MockBankMode): Observable<MockBankStatus> {
    return this.http.put<MockBankStatus>('/api/v1/admin/mock-bank/settings', { mode });
  }

  settleNow(): Observable<MockBankStatus> {
    return this.http.post<MockBankStatus>('/api/v1/admin/mock-bank/settle-now', {});
  }

  injectDiscrepancy(amount: string): Observable<MockBankStatus> {
    return this.http.post<MockBankStatus>('/api/v1/admin/mock-bank/inject-discrepancy', { amount });
  }
}
