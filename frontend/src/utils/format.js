export function formatCurrency(amount, currency = 'TRY', locale = 'tr-TR') {
  if (amount == null) return '';
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(Number(amount));
}
