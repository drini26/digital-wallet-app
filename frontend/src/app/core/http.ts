import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ProblemDetail } from './models';

/** Adds "Authorization: Bearer <JWT>" to every API call when the user is signed in. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token;
  if (!token || !req.url.startsWith('/api/')) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const snackBar = inject(MatSnackBar);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        if (error.status === 401 && !req.url.startsWith('/api/v1/auth/')) {
          auth.logout();
          snackBar.open('Your session ended. Please sign in again.', 'OK', { duration: 5000 });
        } else {
          snackBar.open(problemMessage(error), 'OK', { duration: 6000 });
        }
      }
      return throwError(() => error);
    }),
  );
};

export function problemMessage(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'The server cannot be reached. Check your connection and try again.';
  }
  const problem = (error.error ?? {}) as ProblemDetail;
  const title = problem.title ?? error.statusText ?? 'Error';
  const detail = problem.errors?.join(', ') || problem.detail;
  return detail ? `${title}: ${detail}` : title;
}

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? true : inject(Router).parseUrl('/login');
};

export const managerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isManager() ? true : inject(Router).parseUrl('/wallet');
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? inject(Router).parseUrl(auth.homeUrl()) : true;
};
