import { useState } from 'react';

export default function HeartButton({ defaultActive = false, onToggle }) {
  const [active, setActive] = useState(defaultActive);

  function handle(e) {
    e.preventDefault();
    e.stopPropagation();
    const next = !active;
    setActive(next);
    if (onToggle) onToggle(next);
  }

  return (
    <button
      onClick={handle}
      aria-label="Favorilere ekle"
      className="absolute right-2 top-2 grid h-8 w-8 place-items-center rounded-full bg-white/90 shadow-soft hover:bg-white"
    >
      <svg viewBox="0 0 24 24" className={`h-4 w-4 ${active ? 'text-n11-pink' : 'text-gray-400'}`} fill={active ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8">
        <path d="M12 21s-7-4.35-9-9.5A5.5 5.5 0 0 1 12 6a5.5 5.5 0 0 1 9 5.5C19 16.65 12 21 12 21Z" />
      </svg>
    </button>
  );
}
