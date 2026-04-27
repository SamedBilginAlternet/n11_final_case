import { Headphones, RotateCcw, TicketPercent, Truck } from 'lucide-react';

// Homepage feature strip — pink-soft circle + Lucide stroke icon.
// Same shape as before; consumers read .Icon instead of .icon.
export const features = [
  { id: 'coupons',  Icon: TicketPercent, title: 'Her Alışverişte', subtitle: 'Kupon Fırsatları' },
  { id: 'shipping', Icon: Truck,         title: 'Hızlı ve Güvenli', subtitle: 'Teslimat' },
  { id: 'returns',  Icon: RotateCcw,     title: 'Kolay İade ve',    subtitle: 'Değişim' },
  { id: 'support',  Icon: Headphones,    title: '7/24 Müşteri',     subtitle: 'Desteği' },
];
