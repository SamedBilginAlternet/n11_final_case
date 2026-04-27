import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../state/AuthContext.jsx';
import { useCart } from '../state/CartContext.jsx';

export default function Header() {
  const { user, isAuthed, logout } = useAuth();
  const { cart } = useCart();
  const navigate = useNavigate();

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-6 px-4 py-3">
        <Link to="/" className="flex items-center gap-2">
          <span className="rounded bg-n11-orange px-2 py-1 text-sm font-bold text-white">n11</span>
          <span className="text-sm font-semibold tracking-wide text-slate-700">case</span>
        </Link>

        <nav className="flex items-center gap-4 text-sm">
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'text-n11-orange font-medium' : 'text-slate-600')}>
            Ürünler
          </NavLink>
          {isAuthed && (
            <NavLink to="/orders" className={({ isActive }) => (isActive ? 'text-n11-orange font-medium' : 'text-slate-600')}>
              Siparişlerim
            </NavLink>
          )}
        </nav>

        <div className="flex items-center gap-4 text-sm">
          {isAuthed ? (
            <>
              <Link to="/cart" className="relative rounded-md border px-3 py-1.5 text-slate-700 hover:bg-slate-50">
                Sepet
                {cart.totalQuantity > 0 && (
                  <span className="ml-2 rounded-full bg-n11-orange px-2 py-0.5 text-xs font-semibold text-white">
                    {cart.totalQuantity}
                  </span>
                )}
              </Link>
              <span className="text-slate-500">{user?.fullName}</span>
              <button
                onClick={() => {
                  logout();
                  navigate('/login');
                }}
                className="text-slate-500 hover:text-n11-orange"
              >
                Çıkış
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="text-slate-700 hover:text-n11-orange">
                Giriş
              </Link>
              <Link to="/register" className="btn-primary">
                Kayıt ol
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
