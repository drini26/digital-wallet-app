import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { poll } from '../core/polling';
import { WalletService } from '../core/wallet.service';
import { MoneyPipe } from '../shared/money.pipe';
import { PayDialog } from './pay.dialog';
import { TransferDialog } from './transfer.dialog';

/** The customer's home page: balance (available, pending in, pending out) and recent activity. */
@Component({
  selector: 'app-dashboard',
  imports: [MatButtonModule, MatIconModule, MoneyPipe, DatePipe],
  template: `
    <div class="page-header">
      <h1>Hello, {{ auth.displayName() }}</h1>
      <div class="actions">
        <button mat-flat-button (click)="openPay()" [disabled]="!balance()"><mat-icon>send</mat-icon> Pay</button>
        <button mat-stroked-button (click)="openTransfer()" [disabled]="!balance()">
          <mat-icon>account_balance</mat-icon> Bank transfer
        </button>
      </div>
    </div>

    <section class="cards">
      <div class="tile primary"><div class="label">Available</div><div class="value">{{ balance()?.availableBalance | money }}</div></div>
      <div class="tile"><div class="label">Pending outgoing</div><div class="value">{{ balance()?.pendingOutgoing | money }}</div></div>
      <div class="tile"><div class="label">Pending incoming</div><div class="value">{{ balance()?.pendingIncoming | money }}</div></div>
    </section>

    <section class="panel">
      <h2>Recent activity</h2>
      @if (history(); as items) {
        @if (items.length === 0) {
          <p class="empty">No transactions yet.</p>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Date</th><th>Who / what</th><th>Type</th><th class="right">Amount</th></tr></thead>
              <tbody>
                @for (t of items; track t.transactionId + t.direction) {
                  <tr>
                    <td class="muted">{{ t.createdAt | date: 'dd.MM.yyyy HH:mm' }}</td>
                    <td>{{ t.counterpartyName ?? t.description }}
                      @if (t.counterpartyName && t.description) { <span class="muted">· {{ t.description }}</span> }
                    </td>
                    <td class="muted">{{ label(t.type, t.paymentType) }}</td>
                    <td class="right amount" [class.in]="t.direction === 'IN'" [class.out]="t.direction === 'OUT'">
                      {{ t.direction === 'IN' ? '+' : '-' }}{{ t.amount | money }}
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
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  protected readonly auth = inject(AuthService);
  private readonly wallet = inject(WalletService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  private readonly refresh$ = new Subject<void>();

  protected readonly balance = toSignal(poll(() => this.wallet.balance(), this.refresh$));
  protected readonly history = toSignal(poll(() => this.wallet.history(20), this.refresh$));

  protected openPay(): void {
    this.dialog.open(PayDialog, { width: '440px' }).afterClosed().subscribe((paid) => {
      if (paid) {
        this.snackBar.open('Payment sent', 'OK', { duration: 3000 });
        this.refresh$.next();
      }
    });
  }

  protected openTransfer(): void {
    this.dialog.open(TransferDialog, { width: '480px', data: { walletId: this.balance()!.walletId } })
      .afterClosed().subscribe(() => this.refresh$.next());
  }

  protected label(type: string, paymentType: string | null): string {
    if (type === 'PAYMENT') {
      return paymentType === 'MERCHANT' ? 'Merchant payment' : 'Payment';
    }
    const labels: Record<string, string> = {
      DEPOSIT: 'Deposit', WITHDRAWAL_HOLD: 'Withdrawal', WITHDRAWAL_RELEASED: 'Withdrawal returned',
      REVERSAL: 'Reversal',
    };
    return labels[type] ?? type;
  }
}
