import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { LoginResponse, RegisterRequest, Role, UserLookup, UserProfile } from './models';

/** What we remember about the signed-in user. */
export interface Session {
  token: string;
  userId: string;
  role: Role;
  displayName: string;
  accountType: string;
  expiresAt: string;
}

const STORAGE_KEY = 'wallet.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly state = signal<Session | null>(AuthService.restore());

  readonly session = this.state.asReadonly();
  readonly isAuthenticated = computed(() => {
    const session = this.state();
    return !!session && new Date(session.expiresAt).getTime() > Date.now();
  });
  readonly isManager = computed(() => this.state()?.role === 'MANAGER');
  readonly displayName = computed(() => this.state()?.displayName ?? '');

  get token(): string | null {
    return this.isAuthenticated() ? this.state()!.token : null;
  }

  register(request: RegisterRequest): Observable<unknown> {
    return this.http.post('/api/v1/auth/register', request);
  }

  requestLink(identifier: string): Observable<unknown> {
    return this.http.post('/api/v1/auth/magic-link', { identifier });
  }

  login(identifier: string, token: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/v1/auth/login', { identifier, token }).pipe(
      tap((response) => this.store({
        token: response.accessToken,
        userId: response.userId,
        role: response.role,
        displayName: response.displayName,
        accountType: response.accountType,
        expiresAt: response.expiresAt,
      })),
    );
  }

  lookup(identifier: string): Observable<UserLookup> {
    return this.http.get<UserLookup>('/api/v1/users/lookup', { params: { identifier } });
  }

  updateDisplayName(displayName: string): Observable<UserProfile> {
    return this.http.put<UserProfile>('/api/v1/users/me', { displayName }).pipe(
      tap((profile) => {
        const session = this.state();
        if (session) {
          this.store({ ...session, displayName: profile.displayName });
        }
      }),
    );
  }

  homeUrl(): string {
    return this.isManager() ? '/admin' : '/wallet';
  }

  logout(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.state.set(null);
    void this.router.navigateByUrl('/login');
  }

  private store(session: Session): void {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.state.set(session);
  }

  private static restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as Session) : null;
    } catch {
      return null;
    }
  }
}
