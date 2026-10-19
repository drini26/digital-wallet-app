import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { AdminService } from '../../core/admin.service';
import { MockBankMode } from '../../core/models';
import { poll } from '../../core/polling';
import { MoneyPipe } from '../../shared/money.pipe';

/** Demo controls of the fake partner bank: failure modes, settle now, and inject a 0.01 difference. */
@Component({
  selector: 'app-mock-bank',
  imports: [MatButtonModule, MatButtonToggleModule, MoneyPipe],
  template: `
    <div class="page-header"><h1>Mock bank</h1></div>
    @if (bank(); as b) {
      <section class="panel">
        <h2>Mode</h2>
        <mat-button-toggle-group [value]="b.mode" (change)="setMode($event.value)">
          @for (mode of modes; track mode) {
            <mat-button-toggle [value]="mode">{{ mode }}</mat-button-toggle>
          }
        </mat-button-toggle-group>
        <p class="muted">NORMAL settles after {{ b.settlementDelaySeconds }} s · OFFLINE refuses every call ·
          REJECT_NEXT rejects the next transfer · ACK_LOST processes the next transfer but loses the answer.</p>
      </section>
      <section class="cards">
        <div class="tile"><div class="label">Bank: operating account</div><div class="value">{{ b.operatingBalance | money }}</div></div>
        <div class="tile"><div class="label">Bank: partner reserve</div><div class="value">{{ b.partnerBalance | money }}</div></div>
      </section>
      <section class="panel actions">
        <button mat-flat-button (click)="settleNow()">Settle now</button>
        <button mat-stroked-button (click)="inject('0.01')">Inject +0.01 discrepancy</button>
        <button mat-stroked-button (click)="inject('-0.01')">Inject -0.01 (undo)</button>
      </section>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MockBankPage {
  private readonly admin = inject(AdminService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly refresh$ = new Subject<void>();

  protected readonly modes: MockBankMode[] = ['NORMAL', 'OFFLINE', 'REJECT_NEXT', 'ACK_LOST'];
  protected readonly bank = toSignal(poll(() => this.admin.mockBank(), this.refresh$));

  protected setMode(mode: MockBankMode): void {
    this.admin.setMockBankMode(mode).subscribe(() => {
      this.snackBar.open(`Mock bank is now ${mode}`, 'OK', { duration: 3000 });
      this.refresh$.next();
    });
  }

  protected settleNow(): void {
    this.admin.settleNow().subscribe(() => {
      this.snackBar.open('All accepted transfers settled', 'OK', { duration: 3000 });
      this.refresh$.next();
    });
  }

  protected inject(amount: string): void {
    this.admin.injectDiscrepancy(amount).subscribe(() => {
      this.snackBar.open(`Bank operating balance changed by ${amount}`, 'OK', { duration: 4000 });
      this.refresh$.next();
    });
  }
}
