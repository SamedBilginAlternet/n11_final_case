import { Link, useNavigate } from 'react-router-dom';
import { Heart, LogOut, MapPin, ShoppingBag, User } from 'lucide-react';
import { useAuth } from '../../state/AuthContext.jsx';
import { useCart } from '../../state/CartContext.jsx';
import { useWishlist } from '../../state/WishlistContext.jsx';

/**
 * Header right-side actions. Mobile collapses everything to icon buttons so
 * a 360px-wide screen still fits logo + 3 actions next to it; desktop expands
 * into the labelled "Teslimat Adresi / Profilim" blocks like a real n11.
 */
export default function UserActions() {
  const { user, isAuthed, logout } = useAuth();
  const { cart } = useCart();
  const { favIds } = useWishlist();
  const navigate = useNavigate();

  return (
    <div className="flex items-center gap-2 md:gap-5">
      <DeliveryAddress />
      <FavoritesButton count={favIds.size} />
      <CartButton count={cart.totalQuantity} />
      <AccountBlock
        user={user}
        isAuthed={isAuthed}
        onLogout={() => {
          logout();
          navigate('/login');
        }}
      />
    </div>
  );
}

function DeliveryAddress() {
  return (
    <Link to="/account/addresses" className="hidden items-center gap-2 text-left lg:flex">
      <MapPin className="h-5 w-5 text-gray-500" strokeWidth={1.7} aria-hidden />
      <div className="leading-tight">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">Teslimat Adresi</p>
        <p className="text-sm font-semibold text-gray-800">Adres Ekle</p>
      </div>
    </Link>
  );
}

function FavoritesButton({ count }) {
  return (
    <Link
      to="/favorites"
      className="relative grid h-9 w-9 place-items-center rounded-full bg-gray-100 hover:bg-n11-pinkBg md:h-10 md:w-10"
      aria-label="Favoriler"
    >
      <Heart className="h-5 w-5 text-gray-700" strokeWidth={1.7} aria-hidden />
      {count > 0 && <CountBadge value={count} />}
    </Link>
  );
}

function CartButton({ count }) {
  return (
    <Link
      to="/cart"
      className="relative grid h-9 w-9 place-items-center rounded-full bg-gray-100 hover:bg-n11-pinkBg md:h-10 md:w-10"
      aria-label="Sepet"
    >
      <ShoppingBag className="h-5 w-5 text-gray-700" strokeWidth={1.7} aria-hidden />
      {count > 0 && <CountBadge value={count} />}
    </Link>
  );
}

function CountBadge({ value }) {
  return (
    <span className="absolute -right-1 -top-1 grid h-5 min-w-[20px] place-items-center rounded-full bg-n11-pink px-1 text-[11px] font-bold text-white">
      {value}
    </span>
  );
}

function AccountBlock({ user, isAuthed, onLogout }) {
  if (isAuthed) {
    return (
      <div className="flex items-center gap-1 md:gap-2">
        <Link
          to="/account"
          className="flex items-center gap-2 rounded-full bg-gray-100 p-2 hover:bg-n11-pinkBg md:bg-transparent md:p-0 md:hover:bg-transparent md:hover:text-n11-pink"
          aria-label="Profilim"
        >
          <User className="h-5 w-5 text-gray-700 md:text-gray-500" strokeWidth={1.7} aria-hidden />
          <div className="hidden leading-tight md:block">
            <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">Profilim</p>
            <p className="max-w-[140px] truncate text-sm font-semibold text-gray-800">{user?.fullName}</p>
          </div>
        </Link>
        <button
          type="button"
          onClick={onLogout}
          className="grid h-9 w-9 place-items-center rounded-full text-gray-500 hover:bg-gray-100 hover:text-n11-pink md:hidden"
          aria-label="Çıkış"
        >
          <LogOut className="h-4 w-4" strokeWidth={1.7} aria-hidden />
        </button>
        <button
          type="button"
          onClick={onLogout}
          className="ml-2 hidden text-xs text-gray-500 hover:text-n11-pink md:inline"
        >
          Çıkış
        </button>
      </div>
    );
  }
  return (
    <Link
      to="/login"
      className="flex items-center gap-2 rounded-full bg-gray-100 p-2 hover:bg-n11-pinkBg md:bg-transparent md:p-0 md:hover:bg-transparent"
      aria-label="Giriş yap"
    >
      <User className="h-5 w-5 text-gray-700 md:text-gray-500" strokeWidth={1.7} aria-hidden />
      <div className="hidden leading-tight md:block">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">Hesabım</p>
        <p className="text-sm font-semibold text-gray-800">
          Giriş / Üye Ol
        </p>
      </div>
    </Link>
  );
}
