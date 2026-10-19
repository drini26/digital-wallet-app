import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { ProfileDialog } from './pages/profile.dialog';

/** The frame of every page: the top bar (links depend on the role) and the current page below it. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule, MatIconModule],
  template: `
    <mat-toolbar class="topbar">
      <a class="brand" [routerLink]="auth.isAuthenticated() ? auth.homeUrl() : '/login'">
        <mat-icon>account_balance_wallet</mat-icon>
        <span>Digital Wallet</span>
      </a>
      @if (auth.isAuthenticated()) {
        <nav class="nav">
          @if (auth.isManager()) {
            <a mat-button routerLink="/admin" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Overview</a>
            <a mat-button routerLink="/admin/transactions" routerLinkActive="active">Transactions</a>
            <a mat-button routerLink="/admin/mock-bank" routerLinkActive="active">Mock bank</a>
          } @else {
            <a mat-button routerLink="/wallet" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Wallet</a>
            <a mat-button routerLink="/wallet/transfers" routerLinkActive="active">Bank transfers</a>
          }
        </nav>
        <span class="spacer"></span>
        <button mat-button class="who" (click)="editProfile()" title="Change my display name">
          {{ auth.displayName() }}@if (auth.isManager()) { · Manager }
        </button>
        <button mat-icon-button (click)="auth.logout()" title="Sign out"><mat-icon>logout</mat-icon></button>
      }
    </mat-toolbar>
    <main class="content"><router-outlet /></main>
  `,
  styles: `
    .topbar { position: sticky; top: 0; z-index: 10; gap: 8px; background: var(--wallet-navy); color: #fff; }
    .brand { display: inline-flex; align-items: center; gap: 8px; color: inherit; text-decoration: none;
             font-weight: 500; margin-right: 16px; }
    .brand mat-icon { color: var(--wallet-yellow); }
    .nav a { color: rgba(255, 255, 255, 0.85); }
    .nav a.active { color: var(--wallet-yellow); }
    .spacer { flex: 1; }
    .topbar .who.mat-mdc-button { font-size: 14px; color: #fff; }
    .content { max-width: 1100px; margin: 0 auto; padding: 24px 16px 48px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected editProfile(): void {
    this.dialog.open(ProfileDialog, { width: '400px' });
  }
}
