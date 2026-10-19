import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BankService } from '../core/bank.service';
import { poll } from '../core/polling';
import { MoneyPipe } from '../shared/money.pipe';
import { StatusChipComponent } from '../shared/status-chip.component';

/** The customer's bank transfers and their status, refreshed every 5 seconds. */
@Component({
  selector: 'app-transfers',
  imports: [MoneyPipe, DatePipe, StatusChipComponent],
  template: `
    <div class="page-header"><h1>Bank transfers</h1></div>
    <section class="panel">
      @if (transfers(); as items) {
        @if (items.length === 0) {
          <p class="empty">No bank transfers yet. Use "Bank transfer" on your wallet.</p>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr><th>Date</th><th>Direction</th><th>Account</th><th>Status</th><th>Arrival</th>
                  <th>Attempts</th><th class="right">Amount</th></tr>
              </thead>
              <tbody>
                @for (t of items; track t.trackingId) {
                  <tr>
                    <td class="muted">{{ t.createdAt | date: 'dd.MM.yyyy HH:mm' }}</td>
                    <td>{{ t.transferDirection === 'OUTFLOW' ? 'Withdrawal' : 'Deposit' }}</td>
                    <td class="mono">{{ t.maskedAccountNumber }}</td>
                    <td><app-status-chip [status]="t.status" />
                      @if (t.lastError && t.status !== 'SETTLED') { <div class="muted small">{{ t.lastError }}</div> }
                    </td>
                    <td>{{ t.estimatedArrivalDate | date: 'dd.MM.yyyy' }}</td>
                    <td>{{ t.attemptCount }}</td>
                    <td class="right amount" [class.in]="t.transferDirection === 'INFLOW'" [class.out]="t.transferDirection === 'OUTFLOW'">
                      {{ t.transferDirection === 'OUTFLOW' ? '-' : '+' }}{{ t.amount | money }}
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      } @else {
        <p class="empty">Loading…</p>
      }
    </section>
  `,
  styles: `.small { font-size: 12px; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransfersPage {
  private readonly bank = inject(BankService);
  protected readonly transfers = toSignal(poll(() => this.bank.transfers()));
}
