import { Link } from 'react-router-dom';
import { ShoppingBag, Package, Tags, FolderTree, Users, ArrowUpRight } from 'lucide-react';

export default function DashboardPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Anasayfa</h1>
        <p className="text-sm text-slate-500">Yönetim sayfalarına geçmek için kart seç.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <ShortcutCard to="/orders" icon={ShoppingBag} title="Siparişler" subtitle="Lifecycle yönetimi + kargo" />
        <ShortcutCard to="/products" icon={Package} title="Ürünler" subtitle="Katalog CRUD" />
        <ShortcutCard to="/categories" icon={FolderTree} title="Kategoriler" subtitle="Ürün hiyerarşisi" />
        <ShortcutCard to="/coupons" icon={Tags} title="Kuponlar" subtitle="Yüzde + sabit indirim" />
        <ShortcutCard to="/users" icon={Users} title="Kullanıcılar" subtitle="Rol yönetimi" />
      </div>
    </div>
  );
}

function ShortcutCard({ to, icon: Icon, title, subtitle }) {
  return (
    <Link to={to} className="card group flex items-center justify-between p-5 transition-shadow hover:shadow-md">
      <div className="flex items-center gap-3">
        <div className="grid h-10 w-10 place-items-center rounded-lg bg-brand-50 text-brand-700">
          <Icon size={18} />
        </div>
        <div>
          <p className="text-sm font-semibold tracking-tight">{title}</p>
          <p className="text-xs text-slate-500">{subtitle}</p>
        </div>
      </div>
      <ArrowUpRight size={16} className="text-slate-400 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
    </Link>
  );
}
