import { formatDate, formatMoney, parseMoney } from './money';

describe('parseMoney', () => {
  it('accepts amounts the legacy 9999999.99 field holds', () => {
    expect(parseMoney('117.6')).toBe(117.6);
    expect(parseMoney(' 1,250.00 ')).toBe(1250);
    expect(parseMoney('9999999.99')).toBe(9999999.99);
  });

  it('rejects signs, extra decimals and overflow', () => {
    expect(parseMoney('-5')).toBeNull();
    expect(parseMoney('1.001')).toBeNull();
    expect(parseMoney('10000000')).toBeNull();
    expect(parseMoney('')).toBeNull();
  });
});

describe('formatting', () => {
  it('formats pounds and ACAS dates', () => {
    expect(formatMoney(1234.5)).toBe('£1,234.50');
    expect(formatMoney(null)).toBe('');
    expect(formatDate('2026-10-05')).toBe('05/10/2026');
  });
});
