import { DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subject, catchError, filter, merge, switchMap, timer } from 'rxjs';

/** The UI refreshes every 5 seconds. */
export const REFRESH_INTERVAL_MS = 5000;

export function poll<T>(load: () => Observable<T>, refresh$?: Subject<void>): Observable<T> {
  const destroyRef = inject(DestroyRef);
  const tick$ = timer(0, REFRESH_INTERVAL_MS).pipe(
    filter((tick) => tick === 0 || document.visibilityState === 'visible'),
  );
  const manual$: Observable<unknown> = refresh$ ?? EMPTY;
  return merge(tick$, manual$).pipe(
    switchMap(() => load().pipe(catchError(() => EMPTY))),
    takeUntilDestroyed(destroyRef),
  );
}
