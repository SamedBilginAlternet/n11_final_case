import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../state/AuthContext.jsx';
import { useCart } from '../../state/CartContext.jsx';

export default function UserActions() {
  const { user, isAuthed, logout } = useAuth();
  const { cart } = useCart();
  const navigate = useNavigate();

  return (
    <div className="flex items-center gap-5">
      <DeliveryAddress />
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
      <PinIcon className="h-5 w-5 text-gray-500" />
      <div className="leading-tight">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">Teslimat Adresi</p>
        <p className="text-sm font-semibold text-gray-800">Adres Ekle</p>
      </div>
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
      <BagIcon className="h-5 w-5 text-gray-700" />
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
        <UserIcon className="h-5 w-5 text-gray-500" />
        <div className="leading-tight">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">Hesabım</p>
          <p className="text-sm font-semibold text-gray-800 truncate max-w-[140px]">{user?.fullName}</p>
        </div>
        <button onClick={onLogout} className="ml-2 text-xs text-gray-500 hover:text-n11-pink">
          Çıkış
        </button>
      </div>
    );
  }
  return (
    <Link to="/login" className="flex items-center gap-2">
      <UserIcon className="h-5 w-5 text-gray-500" />
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

function PinIcon({ className }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.8">
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 21s-7-7.58-7-12a7 7 0 1 1 14 0c0 4.42-7 12-7 12Z" />
      <circle cx="12" cy="9" r="2.5" />
    </svg>
  );
}

function BagIcon({ className }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.8">
      <path strokeLinecap="round" strokeLinejoin="round" d="M6 7h12l-1.2 12.1a2 2 0 0 1-2 1.9H9.2a2 2 0 0 1-2-1.9L6 7Z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 7a3 3 0 0 1 6 0" />
    </svg>
  );
}

function UserIcon({ className }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.8">
      <circle cx="12" cy="8" r="4" />
      <path strokeLinecap="round" d="M4 21a8 8 0 0 1 16 0" />
    </svg>
  );
}
