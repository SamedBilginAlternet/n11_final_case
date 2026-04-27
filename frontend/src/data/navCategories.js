import {
  Baby,
  BookOpen,
  Car,
  Dumbbell,
  Shirt,
  Smartphone,
  Sofa,
  Sparkles,
  Zap,
} from 'lucide-react';

// Stroke-icon nav: each category points at /catalog (Süper Fırsatlar) or
// /catalog?category=<slug>. The icon is a Lucide component reference, not a
// string lookup — keeps the render path one indirection shallow and lets
// the bundler tree-shake unused icons.
export const navCategories = [
  { slug: 'super-firsatlar', label: 'Süper Fırsatlar', Icon: Zap },
  { slug: 'moda',            label: 'Moda',            Icon: Shirt },
  { slug: 'elektronik',      label: 'Elektronik',      Icon: Smartphone },
  { slug: 'ev-yasam',        label: 'Ev & Yaşam',      Icon: Sofa },
  { slug: 'spor',            label: 'Spor & Outdoor',  Icon: Dumbbell },
  { slug: 'kozmetik',        label: 'Kozmetik',        Icon: Sparkles },
  { slug: 'anne-bebek',      label: 'Anne & Bebek',    Icon: Baby },
  { slug: 'kitap',           label: 'Kitap & Müzik',   Icon: BookOpen },
  { slug: 'oto-bahce',       label: 'Oto & Bahçe',     Icon: Car },
];
