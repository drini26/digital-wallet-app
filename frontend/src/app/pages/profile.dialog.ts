import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../core/auth.service';

/** Change my display name (PUT /api/v1/users/me), opened from the name in the top bar. */
@Component({
  selector: 'app-profile-dialog',
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>My profile</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content>
        <mat-form-field class="full">
          <mat-label>Display name</mat-label>
          <input matInput formControlName="displayName" maxlength="100" />
          @if (form.invalid) { <mat-error>2 to 100 characters</mat-error> }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [disabled]="pending() || form.invalid">Save</button>
      </mat-dialog-actions>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileDialog {
  private readonly auth = inject(AuthService);
  private readonly ref = inject(MatDialogRef<ProfileDialog, boolean>);

  protected readonly form = new FormGroup({
    displayName: new FormControl(this.auth.displayName(), {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2), Validators.maxLength(100), Validators.pattern(/.*\S.*/)],
    }),
  });
  protected readonly pending = signal(false);

  protected submit(): void {
    if (this.form.invalid || this.pending()) {
      return;
    }
    this.pending.set(true);
    this.auth.updateDisplayName(this.form.getRawValue().displayName.trim()).subscribe({
      next: () => this.ref.close(true),
      error: () => this.pending.set(false),
    });
  }
}
