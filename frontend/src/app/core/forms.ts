import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

const AMOUNT = /^\d{1,15}(\.\d{1,2})?$/;

/** Form validation for amounts: positive, at most two decimals ("25", "25.5", "25.50"). */
export const amountValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  const valid = AMOUNT.test(value) && !/^0+(\.0{1,2})?$/.test(value);
  return valid ? null : { amount: true };
};

export function normalizeAmount(value: string): string {
  const [whole, fraction = ''] = value.trim().split('.');
  return `${whole}.${(fraction + '00').slice(0, 2)}`;
}

const WEIGHTS = [3, 7, 1, 3, 7, 1, 3, 7, 1];

export const routingNumberValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '');
  if (!/^\d{9}$/.test(value)) {
    return { routingNumber: true };
  }
  let sum = 0;
  for (let i = 0; i < 9; i++) {
    sum += WEIGHTS[i] * Number(value[i]);
  }
  return sum % 10 === 0 ? null : { routingNumber: true };
};

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
