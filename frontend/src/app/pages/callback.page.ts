import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { problemMessage } from '../core/http';

/** The page the magic link opens: /auth/callback?token=...&identifier=... */
@Component({
  selector: 'app-callback',
  imports: [MatButtonModule, RouterLink],
  template: `
    <section class="panel box">
      @if (error(); as message) {
        <h1>That link did not work</h1>
        <p>{{ message }}</p>
        <a mat-flat-button routerLink="/login">Request a new link</a>
      } @else {
        <p>Signing you in…</p>
      }
    </section>
  `,
  styles: `.box { max-width: 420px; margin: 48px auto; text-align: center; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CallbackPage implements OnInit {
  readonly token = input<string>();
  readonly identifier = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const token = this.token();
    const identifier = this.identifier();
    if (!token || !identifier) {
      this.error.set('The link is incomplete. Request a new one.');
      return;
    }
    this.auth.login(identifier, token).subscribe({
      next: () => void this.router.navigateByUrl(this.auth.homeUrl(), { replaceUrl: true }),
      error: (e: HttpErrorResponse) => this.error.set(problemMessage(e)),
    });
  }
}
