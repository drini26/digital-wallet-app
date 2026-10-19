import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../core/auth.service';
import { amountValidator, newIdempotencyKey, normalizeAmount } from '../core/forms';
import { PaymentType, UserLookup } from '../core/models';
import { WalletService } from '../core/wallet.service';

/** Pay someone by email or phone. */
@Component({
  selector: 'app-pay-dialog',
  imports: [MatDialogModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule,
    ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>Pay</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content>
        <mat-form-field class="full">
          <mat-label>Recipient email or phone</mat-label>
          <input matInput formControlName="recipient" (blur)="lookup()" placeholder="besi@wallet.local" />
          @if (recipient(); as r) {
            <mat-hint>{{ r.displayName }} · {{ r.accountType === 'MERCHANT' ? 'Merchant' : 'Personal' }}</mat-hint>
          }
        </mat-form-field>
        <mat-button-toggle-group formControlName="type" class="type">
          <mat-button-toggle value="P2P">Person</mat-button-toggle>
          <mat-button-toggle value="MERCHANT">Merchant</mat-button-toggle>
        </mat-button-toggle-group>
        <mat-form-field class="full">
          <mat-label>Amount</mat-label>
          <input matInput formControlName="amount" inputmode="decimal" placeholder="25.00" />
          <span matTextSuffix>EUR</span>
          @if (form.controls.amount.invalid) { <mat-error>Enter an amount like 25 or 25.50</mat-error> }
        </mat-form-field>
        <mat-form-field class="full">
          <mat-label>Description (optional)</mat-label>
          <input matInput formControlName="description" maxlength="200" />
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [disabled]="pending() || !recipient() || form.invalid">Pay</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.type { margin: 8px 0 20px; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayDialog {
  private readonly auth = inject(AuthService);
  private readonly wallet = inject(WalletService);
  private readonly ref = inject(MatDialogRef<PayDialog, boolean>);
  private readonly idempotencyKey = newIdempotencyKey();

  protected readonly form = new FormGroup({
    recipient: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    type: new FormControl<PaymentType>('P2P', { nonNullable: true }),
    amount: new FormControl('', { nonNullable: true, validators: [Validators.required, amountValidator] }),
    description: new FormControl('', { nonNullable: true }),
  });
  protected readonly recipient = signal<UserLookup | null>(null);
  protected readonly pending = signal(false);

  protected lookup(): void {
    const identifier = this.form.controls.recipient.value.trim();
    this.recipient.set(null);
    if (!identifier) {
      return;
    }
    this.auth.lookup(identifier).subscribe((user) => {
      this.recipient.set(user);
      this.form.controls.type.setValue(user.accountType === 'MERCHANT' ? 'MERCHANT' : 'P2P');
    });
  }

  protected submit(): void {
    const recipient = this.recipient();
    if (!recipient || this.form.invalid || this.pending()) {
      return;
    }
    this.pending.set(true);
    const value = this.form.getRawValue();
    this.wallet.pay({
      recipientId: recipient.userId,
      amount: normalizeAmount(value.amount),
      currency: 'EUR',
      transactionType: value.type,
      description: value.description || undefined,
    }, this.idempotencyKey).subscribe({
      next: () => this.ref.close(true),
      error: () => this.pending.set(false),
    });
  }
}
