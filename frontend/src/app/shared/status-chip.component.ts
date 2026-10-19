import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const TONES: Record<string, string> = {
  SUCCESS: 'ok', SETTLED: 'ok', RECONCILED: 'ok', COMPLETED: 'ok',
  QUEUED: 'info', REQUESTED: 'info', SENT: 'info',
  PENDING_RETRY: 'warn', REVERSED: 'warn',
  FAILED: 'bad', REJECTED: 'bad', MISMATCHED: 'bad',
};

/** A status as a small coloured label (green = done, blue = on its way, yellow = retrying, red = failed). */
@Component({
  selector: 'app-status-chip',
  template: `<span class="chip" [class]="tone()">{{ label() }}</span>`,
  styles: `
    .chip { display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 12px; font-weight: 500;
            white-space: nowrap; }
    .ok { background: #e3f4e8; color: #1b5e20; }
    .info { background: #e3ecfa; color: #0d3c8a; }
    .warn { background: #fff4d6; color: #7a4f00; }
    .bad { background: #fde3e3; color: #8e1b1b; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusChipComponent {
  readonly status = input.required<string>();
  protected readonly label = computed(() => this.status().replaceAll('_', ' '));
  protected readonly tone = computed(() => `chip ${TONES[this.status()] ?? 'info'}`);
}
