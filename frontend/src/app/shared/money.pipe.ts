import { Pipe, PipeTransform } from '@angular/core';

/** Formats an amount in euro, e.g. 1234.5 -> "1.234,50 €". */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number | string | null | undefined, currency = 'EUR'): string {
    if (amount === null || amount === undefined || amount === '') {
      return '';
    }
    const text = typeof amount === 'number' ? amount.toFixed(2) : amount;
    const negative = text.startsWith('-');
    const [whole, fraction = ''] = (negative ? text.slice(1) : text).split('.');
    const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    const cents = (fraction + '00').slice(0, 2);
    const symbol = currency === 'EUR' ? '€' : currency;
    return `${negative ? '-' : ''}${grouped},${cents} ${symbol}`;
  }
}
