import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { AdminService } from '../../core/admin.service';
import { Reconciliation } from '../../core/models';
import { poll } from '../../core/polling';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusChipComponent } from '../../shared/status-chip.component';

/** The manager's overview: metrics, the frozen banner with Unfreeze, and the reconcile, sweep and batch actions. */
@Component({
  selector: 'app-admin-dashboard',
  imports: [MatButtonModule, MoneyPipe, DatePipe, StatusChipComponent],
  template: `
    <div class="page-header">
      <h1>Operations</h1>
      <div class="actions">
        <button mat-flat-button (click)="reconcile()">Run reconciliation</button>
        <button mat-stroked-button (click)="sweep()">Run sweep</button>
        <button mat-stroked-button (click)="runBatch()">Run bank batch now</button>
      </div>
    </div>

    @if (status()?.outgoingFrozen) {
      <div class="banner-frozen" role="alert">
        <strong>Outgoing money is frozen.</strong>
        <span>{{ status()?.frozenReason }}</span>
        <span class="spacer"></span>
        <button mat-flat-button (click)="unfreeze()">Unfreeze</button>
      </div>
    }

    @if (metrics(); as m) {
      <section class="cards">
        <div class="tile"><div class="label">Payment volume (24 h)</div><div class="value">{{ m.totalVolume | money }}</div></div>
        <div class="tile"><div class="label">Payments</div><div class="value">{{ m.paymentCount }}</div></div>
        <div class="tile"><div class="label">Active users</div><div class="value">{{ m.activeUsers }}</div></div>
        <div class="tile"><div class="label">Rejected payments</div><div class="value">{{ m.rejectedPayments }}</div></div>
        <div class="tile"><div class="label">System errors (24 h)</div><div class="value">{{ m.systemErrorCount }}</div></div>
        <div class="tile"><div class="label">Avg processing</div><div class="value">{{ m.averageProcessingMs }} ms</div></div>
        <div class="tile"><div class="label">Pending transfers</div><div class="value">{{ m.pendingTransfers }}</div></div>
      </section>
    }

    @if (lastReconciliation(); as r) {
      <section class="panel">
        <h2>Last reconciliation <app-status-chip [status]="r.status" /></h2>
        <p class="muted">{{ r.createdAt | date: 'dd.MM.yyyy HH:mm:ss' }} · difference {{ r.difference | money }}</p>
        <table class="data">
          <thead><tr><th></th><th class="right">Ledger</th><th class="right">Bank</th></tr></thead>
          <tbody>
            <tr><td>Operating account</td><td class="right">{{ r.ledgerOperating | money }}</td><td class="right">{{ r.bankOperating | money }}</td></tr>
            <tr><td>Partner reserve</td><td class="right">{{ r.ledgerPartner | money }}</td><td class="right">{{ r.bankPartner | money }}</td></tr>
            <tr><td>All wallets</td><td class="right">{{ r.walletsTotal | money }}</td><td></td></tr>
            @if (r.outgoingPending !== null) {
              <tr><td>Withdrawals in progress</td><td class="right">{{ r.outgoingPending | money }}</td><td></td></tr>
            }
          </tbody>
        </table>
        @for (d of r.discrepancies; track $index) {
          <p class="out">{{ d }}</p>
        }
      </section>
    }

    <section class="panel">
      <h2>Error log</h2>
      @if (errors(); as items) {
        @if (items.length === 0) {
          <p class="empty">No errors recorded.</p>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Time</th><th>Status</th><th>Code</th><th>Message</th></tr></thead>
              <tbody>
                @for (e of items; track e.id) {
                  <tr>
                    <td class="muted">{{ e.occurredAt | date: 'dd.MM.yyyy HH:mm:ss' }}</td>
                    <td>{{ e.httpStatus }}</td>
                    <td class="mono">{{ e.code }}</td>
                    <td>{{ e.message }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    </section>
  `,
  styles: `.spacer { flex: 1; } h2 { display: flex; gap: 12px; align-items: center; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminDashboardPage {
  private readonly admin = inject(AdminService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly refresh$ = new Subject<void>();

  protected readonly metrics = toSignal(poll(() => this.admin.metrics(), this.refresh$));
  protected readonly status = toSignal(poll(() => this.admin.systemStatus(), this.refresh$));
  protected readonly errors = toSignal(poll(() => this.admin.errors(), this.refresh$));
  protected readonly lastReconciliation = signal<Reconciliation | null>(null);

  constructor() {
    this.admin.reconciliations().subscribe((runs) => this.lastReconciliation.set(runs[0] ?? null));
  }

  protected reconcile(): void {
    this.admin.reconcile().subscribe((result) => {
      this.lastReconciliation.set(result);
      this.snackBar.open(`Reconciliation: ${result.status}`, 'OK', { duration: 4000 });
      this.refresh$.next();
    });
  }

  protected sweep(): void {
    this.admin.sweep().subscribe((result) => {
      this.snackBar.open(`Swept ${result.amount.toFixed(2)} EUR to the partner bank`, 'OK', { duration: 5000 });
      this.refresh$.next();
    });
  }

  protected runBatch(): void {
    this.admin.runBatch().subscribe((r) => {
      this.snackBar.open(`Batch: ${r.sent} sent, ${r.retrying} retrying, ${r.rejected} rejected`
        + (r.skippedFrozen ? `, ${r.skippedFrozen} withdrawals held (frozen)` : ''), 'OK', { duration: 6000 });
      this.refresh$.next();
    });
  }

  protected unfreeze(): void {
    this.admin.unfreeze().subscribe(() => {
      this.snackBar.open('Outgoing money unfrozen', 'OK', { duration: 4000 });
      this.refresh$.next();
    });
  }
}
