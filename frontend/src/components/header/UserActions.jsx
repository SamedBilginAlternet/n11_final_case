import { Link, useNavigate } from 'react-router-dom';
import { Heart, MapPin, ShoppingBag, User } from 'lucide-react';
import { useAuth } from '../../state/AuthContext.jsx';
import { useCart } from '../../state/CartContext.jsx';
import { useWishlist } from '../../state/WishlistContext.jsx';

export default function UserActions() {
  const { user, isAuthed, logout } = useAuth();
  const { cart } = useCart();
  const { favIds } = useWishlist();
  const navigate = useNavigate();

  return (
    <div className="flex items-center gap-5">
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
      className="relative grid h-10 w-10 place-items-center rounded-full bg-gray-100 hover:bg-n11-pinkBg"
      aria-label="Favoriler"
    >
      <Heart className="h-5 w-5 text-gray-700" strokeWidth={1.7} aria-hidden />
      {count > 0 && (
        <span className="absolute -right-1 -top-1 grid h-5 min-w-[20px] place-items-center rounded-full bg-n11-pink px-1 text-[11px] font-bold text-white">
          {count}
        </span>
      )}
    </Link>
  );
}

function CartButton({ count }) {
  return (
    <Link
      to="/cart"
      className="relative grid h-10 w-10 place-items-center rounded-full bg-gray-100 hover:bg-n11-pinkBg"
      aria-label="Sepet"
    >
      <ShoppingBag className="h-5 w-5 text-gray-700" strokeWidth={1.7} aria-hidden />
      {count > 0 && (
        <span className="absolute -right-1 -top-1 grid h-5 min-w-[20px] place-items-center rounded-full bg-n11-pink px-1 text-[11px] font-bold text-white">
          {count}
        </span>
      )}
    </Link>
  );
}

function AccountBlock({ user, isAuthed, onLogout }) {
  if (isAuthed) {
    return (
      <div className="flex items-center gap-2">
        <Link to="/account" className="flex items-center gap-2 hover:text-n11-pink">
          <User className="h-5 w-5 text-gray-500" strokeWidth={1.7} aria-hidden />
          <div className="leading-tight">
            <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">Profilim</p>
            <p className="max-w-[140px] truncate text-sm font-semibold text-gray-800">{user?.fullName}</p>
          </div>
        </Link>
        <button onClick={onLogout} className="ml-2 text-xs text-gray-500 hover:text-n11-pink">
          Çıkış
        </button>
      </div>
    );
  }
  return (
    <Link to="/login" className="flex items-center gap-2">
      <User className="h-5 w-5 text-gray-500" strokeWidth={1.7} aria-hidden />
      <div className="leading-tight">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">Hesabım</p>
        <p className="text-sm font-semibold text-gray-800">
          <Link to="/register" className="hover:text-n11-pink">Üye Ol</Link>
          <span className="mx-1 text-gray-300">|</span>
          <span className="hover:text-n11-pink">Giriş Yap</span>
        </p>
      </div>
    </Link>
  );
}
