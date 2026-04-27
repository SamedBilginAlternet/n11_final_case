import { Compass, Gift, Sparkles, Ticket } from 'lucide-react';

export const quickLinks = [
  { id: 'discover',   label: 'Ürünleri Keşfet',  to: '/catalog',     Icon: Compass },
  { id: 'coupons',    label: 'Kuponlar',         to: '/coupons',     Icon: Ticket },
  { id: 'campaigns',  label: 'Kampanyalar',      to: '/campaigns',   Icon: Sparkles },
  { id: 'gift-cards', label: 'Hediye Kartları',  to: '/gift-cards',  Icon: Gift },
];
