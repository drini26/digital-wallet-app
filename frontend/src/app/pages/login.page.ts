import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTabsModule } from '@angular/material/tabs';
import { Observable } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { AccountType } from '../core/models';

/** Two tabs: "Sign in" (send me a link) and "Create account" (sign up). */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatTabsModule, MatFormFieldModule, MatInputModule, MatButtonModule,
    MatButtonToggleModule],
  template: `
    <section class="panel login">
      @if (sentTo(); as identifier) {
        <h1>Check your inbox</h1>
        <p>If <strong>{{ identifier }}</strong> is valid, a one-time sign-in link is on its way.
          It works once and expires in 15 minutes.</p>
        <p class="muted">Demo: emails and text messages arrive in
          <a href="http://localhost:8025" target="_blank" rel="noopener">Mailpit</a>
          (phone messages go to <span class="mono">+355...@sms.wallet.local</span>).</p>
        <button mat-button (click)="sentTo.set(null)">Use another email or phone</button>
      } @else {
        <mat-tab-group mat-stretch-tabs dynamicHeight animationDuration="0ms">
          <mat-tab label="Sign in">
            <form [formGroup]="signInForm" (ngSubmit)="signIn()">
              <p class="muted">No password: we send you a one-time link.</p>
              <mat-form-field class="full">
                <mat-label>Email or phone</mat-label>
                <input matInput formControlName="identifier" placeholder="ana@wallet.local" />
              </mat-form-field>
              <button mat-flat-button class="full" type="submit" [disabled]="sending()">Send sign-in link</button>
            </form>
          </mat-tab>
          <mat-tab label="Create account">
            <form [formGroup]="registerForm" (ngSubmit)="register()">
              <p class="muted">Personal accounts pay people and shops; merchant accounts receive payments.</p>
              <mat-form-field class="full">
                <mat-label>Display name</mat-label>
                <input matInput formControlName="displayName" placeholder="Dora Customer" />
              </mat-form-field>
              <mat-form-field class="full">
                <mat-label>Email or phone (+355...)</mat-label>
                <input matInput formControlName="identifier" placeholder="you@example.com" />
              </mat-form-field>
              <mat-button-toggle-group formControlName="accountType" class="full type">
                <mat-button-toggle value="PERSONAL">Personal</mat-button-toggle>
                <mat-button-toggle value="MERCHANT">Merchant</mat-button-toggle>
              </mat-button-toggle-group>
              <button mat-flat-button class="full" type="submit" [disabled]="sending()">Create account</button>
            </form>
          </mat-tab>
        </mat-tab-group>
      }
    </section>
  `,
  styles: `
    .login { max-width: 440px; margin: 48px auto; }
    form { padding-top: 12px; }
    .type { display: flex; margin-bottom: 16px; }
    .type mat-button-toggle { flex: 1; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  protected readonly sending = signal(false);
  protected readonly sentTo = signal<string | null>(null);

  protected readonly signInForm = new FormGroup({
    identifier: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly registerForm = new FormGroup({
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    identifier: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    accountType: new FormControl<AccountType>('PERSONAL', { nonNullable: true }),
  });

  protected signIn(): void {
    if (this.signInForm.invalid) {
      return;
    }
    const identifier = this.signInForm.controls.identifier.value.trim();
    this.send(this.auth.requestLink(identifier), identifier);
  }

  protected register(): void {
    if (this.registerForm.invalid) {
      return;
    }
    const value = this.registerForm.getRawValue();
    const identifier = value.identifier.trim();
    this.send(this.auth.register({ displayName: value.displayName.trim(), identifier, accountType: value.accountType }),
      identifier);
  }

  private send(request: Observable<unknown>, identifier: string): void {
    this.sending.set(true);
    request.subscribe({
      next: () => {
        this.sending.set(false);
        this.sentTo.set(identifier);
      },
      error: () => this.sending.set(false),
    });
  }
}
