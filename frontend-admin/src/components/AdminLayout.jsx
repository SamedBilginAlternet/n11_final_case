import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { LayoutDashboard, Package, ShoppingBag, Tags, LogOut, Sparkles, FolderTree, Users } from 'lucide-react';
import clsx from 'clsx';
import { useAuth } from '../state/AuthContext.jsx';

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="flex min-h-screen bg-slate-50 text-slate-800">
      <aside className="hidden w-64 flex-shrink-0 flex-col border-r border-slate-200 bg-white p-4 lg:flex">
        <div className="flex items-center gap-2 px-2 py-3">
          <div className="grid h-9 w-9 place-items-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 text-white">
            <Sparkles size={18} />
          </div>
          <div>
            <p className="text-sm font-bold tracking-tight">n11 Admin</p>
            <p className="text-[11px] text-slate-500">Back-office paneli</p>
          </div>
        </div>

        <nav className="mt-6 space-y-1">
          <NavItem to="/" icon={LayoutDashboard} label="Anasayfa" end />
          <NavItem to="/orders" icon={ShoppingBag} label="Siparişler" />
          <NavItem to="/products" icon={Package} label="Ürünler" />
          <NavItem to="/categories" icon={FolderTree} label="Kategoriler" />
          <NavItem to="/coupons" icon={Tags} label="Kuponlar" />
          <NavItem to="/users" icon={Users} label="Kullanıcılar" />
        </nav>

        <div className="mt-auto rounded-md border border-slate-200 bg-slate-50 p-3 text-xs">
          <p className="font-semibold text-slate-700">{user?.fullName || user?.email}</p>
          <p className="text-slate-500">{user?.email}</p>
          <button onClick={onLogout} className="mt-2 inline-flex items-center gap-1 text-rose-600 hover:underline">
            <LogOut size={12} /> Çıkış Yap
          </button>
        </div>
      </aside>

      <main className="min-w-0 flex-1">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3 lg:hidden">
          <p className="text-sm font-bold">n11 Admin</p>
          <button onClick={onLogout} className="text-xs text-rose-600">Çıkış</button>
        </header>
        <div className="mx-auto max-w-7xl p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

function NavItem({ to, icon: Icon, label, end }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        clsx(
          'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors',
          isActive
            ? 'bg-brand-50 text-brand-700'
            : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900',
        )
      }
    >
      <Icon size={16} />
      {label}
    </NavLink>
  );
}
