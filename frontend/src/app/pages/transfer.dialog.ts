import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { BankService } from '../core/bank.service';
import { amountValidator, newIdempotencyKey, normalizeAmount, routingNumberValidator } from '../core/forms';
import { Transfer, TransferDirection } from '../core/models';
import { MoneyPipe } from '../shared/money.pipe';

/** Withdraw to (OUTFLOW) or deposit from (INFLOW) a bank account. */
@Component({
  selector: 'app-transfer-dialog',
  imports: [MatDialogModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule,
    ReactiveFormsModule, MoneyPipe, DatePipe],
  template: `
    <h2 mat-dialog-title>Bank transfer</h2>
    @if (result(); as r) {
      <mat-dialog-content>
        <p><strong>{{ r.transferDirection === 'OUTFLOW' ? 'Withdrawal' : 'Deposit' }} of {{ r.amount | money }}</strong>
          is queued for the next bank batch.</p>
        <p>Estimated arrival: <strong>{{ r.estimatedArrivalDate | date: 'EEEE, d MMMM y' }}</strong></p>
        <p class="muted mono">Tracking id {{ r.trackingId }}</p>
      </mat-dialog-content>
      <mat-dialog-actions align="end"><button mat-flat-button mat-dialog-close>Done</button></mat-dialog-actions>
    } @else {
      <form [formGroup]="form" (ngSubmit)="submit()">
        <mat-dialog-content>
          <mat-button-toggle-group formControlName="direction" class="direction">
            <mat-button-toggle value="OUTFLOW">Withdraw to bank</mat-button-toggle>
            <mat-button-toggle value="INFLOW">Deposit from bank</mat-button-toggle>
          </mat-button-toggle-group>
          <mat-form-field class="full">
            <mat-label>Routing number</mat-label>
            <input matInput formControlName="routing" maxlength="9" placeholder="021000021" />
            @if (form.controls.routing.invalid) { <mat-error>Not a valid 9-digit routing number</mat-error> }
          </mat-form-field>
          <mat-form-field class="full">
            <mat-label>Account number</mat-label>
            <input matInput formControlName="account" maxlength="17" placeholder="123456789" />
            @if (form.controls.account.invalid) { <mat-error>4 to 17 digits</mat-error> }
          </mat-form-field>
          <mat-form-field class="full">
            <mat-label>Amount</mat-label>
            <input matInput formControlName="amount" inputmode="decimal" placeholder="100.00" />
            <span matTextSuffix>EUR</span>
            @if (form.controls.amount.invalid) { <mat-error>Enter an amount like 100 or 100.50</mat-error> }
          </mat-form-field>
        </mat-dialog-content>
        <mat-dialog-actions align="end">
          <button mat-button type="button" mat-dialog-close>Cancel</button>
          <button mat-flat-button type="submit" [disabled]="pending() || form.invalid">Send</button>
        </mat-dialog-actions>
      </form>
    }
  `,
  styles: `.direction { margin-bottom: 20px; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransferDialog {
  private readonly bank = inject(BankService);
  private readonly data = inject<{ walletId: string }>(MAT_DIALOG_DATA);
  private readonly idempotencyKey = newIdempotencyKey();

  protected readonly form = new FormGroup({
    direction: new FormControl<TransferDirection>('OUTFLOW', { nonNullable: true }),
    routing: new FormControl('021000021', { nonNullable: true, validators: [Validators.required, routingNumberValidator] }),
    account: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{4,17}$/)] }),
    amount: new FormControl('', { nonNullable: true, validators: [Validators.required, amountValidator] }),
  });
  protected readonly pending = signal(false);
  protected readonly result = signal<Transfer | null>(null);

  protected submit(): void {
    if (this.form.invalid || this.pending()) {
      return;
    }
    this.pending.set(true);
    const value = this.form.getRawValue();
    this.bank.createTransfer({
      sourceWalletId: this.data.walletId,
      bankRoutingNumber: value.routing,
      externalAccountNumber: value.account,
      transferDirection: value.direction,
      amount: normalizeAmount(value.amount),
      currency: 'EUR',
    }, this.idempotencyKey).subscribe({
      next: (transfer) => this.result.set(transfer),
      error: () => this.pending.set(false),
    });
  }
}
