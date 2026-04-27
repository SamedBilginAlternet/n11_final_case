import { motion } from 'framer-motion';
import { useWishlist } from '../../state/WishlistContext.jsx';

export default function HeartButton({ productId, className = '' }) {
  const { isFavourite, toggle } = useWishlist();
  const active = productId ? isFavourite(productId) : false;

  function handle(e) {
    e.preventDefault();
    e.stopPropagation();
    if (productId) toggle(productId);
  }

  return (
    <motion.button
      type="button"
      onClick={handle}
      aria-label={active ? 'Favorilerden çıkar' : 'Favorilere ekle'}
      whileTap={{ scale: 0.85 }}
      animate={active ? { scale: [1, 1.25, 1] } : {}}
      transition={{ duration: 0.32 }}
      className={`absolute right-2 top-2 grid h-9 w-9 place-items-center rounded-full bg-white/95 shadow-soft outline-none transition hover:bg-white ${className}`}
    >
      <svg
        viewBox="0 0 24 24"
        className={`h-5 w-5 ${active ? 'text-n11-pink' : 'text-gray-400'}`}
        fill={active ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="1.8"
      >
        <path d="M12 21s-7-4.35-9-9.5A5.5 5.5 0 0 1 12 6a5.5 5.5 0 0 1 9 5.5C19 16.65 12 21 12 21Z" />
      </svg>
    </motion.button>
  );
}
