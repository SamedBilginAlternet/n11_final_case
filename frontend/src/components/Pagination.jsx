export default function Pagination({ page, totalPages, totalElements, onChange }) {
  if (totalPages <= 1) return null;
  const pages = [];
  const start = Math.max(0, page - 2);
  const end = Math.min(totalPages, start + 5);
  for (let i = start; i < end; i += 1) pages.push(i);

  return (
    <nav className="flex flex-col gap-3 border-t border-gray-200 pt-4 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-xs text-gray-500 sm:text-sm">
        Toplam <strong className="text-gray-700">{totalElements}</strong> ürün — sayfa {page + 1}/{totalPages}
      </p>
      <div className="flex flex-wrap justify-end gap-1.5 sm:gap-2">
        <button
          onClick={() => onChange(Math.max(0, page - 1))}
          disabled={page === 0}
          className="btn-outline px-3 text-xs sm:text-sm"
        >
          Önceki
        </button>
        {pages.map((p) => (
          <button
            key={p}
            onClick={() => onChange(p)}
            className={`btn min-w-[40px] px-2.5 text-xs sm:text-sm ${p === page ? 'bg-n11-pink text-white' : 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50'}`}
          >
            {p + 1}
          </button>
        ))}
        <button
          onClick={() => onChange(Math.min(totalPages - 1, page + 1))}
          disabled={page >= totalPages - 1}
          className="btn-outline px-3 text-xs sm:text-sm"
        >
          Sonraki
        </button>
      </div>
    </nav>
  );
}
