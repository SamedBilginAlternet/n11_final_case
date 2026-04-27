export default function RatingStars({ value = 0, count = 0, size = 'sm' }) {
  const v = Math.max(0, Math.min(5, Number(value)));
  const sizeClass = size === 'sm' ? 'h-3.5 w-3.5' : 'h-4 w-4';

  return (
    <div className="flex items-center gap-1">
      <div className="flex">
        {Array.from({ length: 5 }).map((_, i) => (
          <Star key={i} filled={i + 1 <= Math.round(v)} className={sizeClass} />
        ))}
      </div>
      {count > 0 && <span className="text-xs text-gray-500">({count})</span>}
    </div>
  );
}

function Star({ filled, className }) {
  return (
    <svg viewBox="0 0 24 24" className={`${className} ${filled ? 'text-amber-400' : 'text-gray-300'}`} fill="currentColor">
      <path d="M12 2 14.6 8.6 21.6 9.3 16.3 13.9 17.9 21 12 17.3 6.1 21 7.7 13.9 2.4 9.3 9.4 8.6Z" />
    </svg>
  );
}
