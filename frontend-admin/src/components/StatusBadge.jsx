import clsx from 'clsx';

const PALETTE = {
  PENDING:          { bg: 'bg-slate-100',   fg: 'text-slate-700',   label: 'Beklemede' },
  AWAITING_PAYMENT: { bg: 'bg-amber-100',   fg: 'text-amber-700',   label: 'Ödeme Bekliyor' },
  CONFIRMED:        { bg: 'bg-blue-100',    fg: 'text-blue-700',    label: 'Onaylandı' },
  PROCESSING:       { bg: 'bg-indigo-100',  fg: 'text-indigo-700',  label: 'Hazırlanıyor' },
  SHIPPED:          { bg: 'bg-violet-100',  fg: 'text-violet-700',  label: 'Kargoda' },
  DELIVERED:        { bg: 'bg-emerald-100', fg: 'text-emerald-700', label: 'Teslim Edildi' },
  CANCELLED:        { bg: 'bg-rose-100',    fg: 'text-rose-700',    label: 'İptal' },
};

export default function StatusBadge({ status }) {
  const p = PALETTE[status] || { bg: 'bg-slate-100', fg: 'text-slate-700', label: status };
  return (
    <span className={clsx('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold', p.bg, p.fg)}>
      {p.label}
    </span>
  );
}

export const STATUS_OPTIONS = [
  { value: '', label: 'Tümü' },
  { value: 'CONFIRMED', label: 'Onaylandı' },
  { value: 'PROCESSING', label: 'Hazırlanıyor' },
  { value: 'SHIPPED', label: 'Kargoda' },
  { value: 'DELIVERED', label: 'Teslim Edildi' },
  { value: 'CANCELLED', label: 'İptal' },
];
