import { formatCurrency } from './format.js';

describe('formatCurrency', () => {
  it('formats TRY amounts with Turkish locale', () => {
    const result = formatCurrency(1234.5, 'TRY');
    expect(result).toContain('1.234,50');
    expect(result).toMatch(/(₺|TL)/);
  });

  it('returns empty string when amount is null', () => {
    expect(formatCurrency(null, 'TRY')).toBe('');
  });
});
