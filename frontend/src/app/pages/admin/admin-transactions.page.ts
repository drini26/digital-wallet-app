import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminService } from '../../core/admin.service';
import { AdminTransaction } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusChipComponent } from '../../shared/status-chip.component';

/** Recent payments of all users. */
@Component({
  selector: 'app-admin-transactions',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MoneyPipe, DatePipe, StatusChipComponent],
  template: `
    <div class="page-header">
      <h1>Transactions</h1>
      <mat-form-field subscriptSizing="dynamic" class="reason">
        <mat-label>Reason for a reversal</mat-label>
        <input matInput [ngModel]="reason()" (ngModelChange)="reason.set($event)" maxlength="150"
               placeholder="Customer complaint" />
      </mat-form-field>
    </div>
    <section class="panel table-wrap">
      <table class="data">
        <thead><tr><th>Date</th><th>Type</th><th>From → To</th><th>Description</th><th class="right">Amount</th><th></th></tr></thead>
        <tbody>
          @for (t of rows(); track t.transactionId) {
            <tr>
              <td class="muted">{{ t.createdAt | date: 'dd.MM.yyyy HH:mm' }}</td>
              <td>{{ t.type === 'PAYMENT' ? (t.paymentType === 'MERCHANT' ? 'Merchant payment' : 'Payment') : 'Reversal' }}</td>
              <td>{{ t.payerName }} → {{ t.payeeName }}</td>
              <td class="muted">{{ t.description }}</td>
              <td class="right amount">{{ t.amount | money }}</td>
              <td class="right">
                @if (t.type === 'PAYMENT' && !t.reversed) {
                  <button mat-stroked-button (click)="reverse(t)" [disabled]="!reason().trim()">Reverse</button>
                } @else if (t.reversed) {
                  <app-status-chip status="REVERSED" />
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
      @if (rows().length === 0) { <p class="empty">No payments yet.</p> }
    </section>
  `,
  styles: `.reason { width: 320px; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminTransactionsPage {
  private readonly admin = inject(AdminService);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly rows = signal<AdminTransaction[]>([]);
  protected readonly reason = signal('');

  constructor() {
    this.load();
  }

  protected reverse(transaction: AdminTransaction): void {
    this.admin.reverse(transaction.transactionId, this.reason().trim()).subscribe(() => {
      this.snackBar.open('Payment reversed', 'OK', { duration: 4000 });
      this.load();
    });
  }

  private load(): void {
    this.admin.transactions().subscribe((rows) => this.rows.set(rows));
  }
}
