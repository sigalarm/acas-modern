const formatter = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' });

export function formatMoney(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : formatter.format(value);
}

/** Parses an amount typed by the user: up to 7 digits and 2 decimals, no sign. */
export function parseMoney(text: string): number | null {
  const trimmed = text.trim().replace(/,/g, '');
  if (!/^\d{1,7}(\.\d{0,2})?$/.test(trimmed)) {
    return null;
  }
  return Number(trimmed);
}

/** ISO date to the dd/mm/ccyy form ACAS shows. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  const [year, month, day] = iso.split('-');
  return `${day}/${month}/${year}`;
}
