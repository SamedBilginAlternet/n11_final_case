export function formatCurrency(amount, currency = 'TRY') {
  if (amount == null) return '—';
  try {
    return new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(Number(amount));
  } catch {
    return `${amount} ${currency}`;
  }
}

export function formatDate(iso) {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat('tr-TR', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function relativeTime(iso) {
  if (!iso) return '';
  const diffMs = Date.now() - new Date(iso).getTime();
  const min = Math.round(diffMs / 60000);
  if (min < 1) return 'az önce';
  if (min < 60) return `${min} dk önce`;
  const h = Math.round(min / 60);
  if (h < 24) return `${h} sa önce`;
  return `${Math.round(h / 24)} gün önce`;
}
