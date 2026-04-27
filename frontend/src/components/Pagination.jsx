export default function Pagination({ page, totalPages, totalElements, onChange }) {
  if (totalPages <= 1) return null;
  const pages = [];
  const start = Math.max(0, page - 2);
  const end = Math.min(totalPages, start + 5);
  for (let i = start; i < end; i += 1) pages.push(i);

  return (
    <nav className="flex items-center justify-between border-t border-slate-200 pt-4">
      <p className="text-sm text-slate-500">
        Toplam <strong className="text-slate-700">{totalElements}</strong> ürün — sayfa {page + 1}/{totalPages}
      </p>
      <div className="flex gap-2">
        <button
          onClick={() => onChange(Math.max(0, page - 1))}
          disabled={page === 0}
          className="btn-outline"
        >
          Önceki
        </button>
        {pages.map((p) => (
          <button
            key={p}
            onClick={() => onChange(p)}
            className={`btn ${p === page ? 'bg-n11-orange text-white' : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50'}`}
          >
            {p + 1}
          </button>
        ))}
        <button
          onClick={() => onChange(Math.min(totalPages - 1, page + 1))}
          disabled={page >= totalPages - 1}
          className="btn-outline"
        >
          Sonraki
        </button>
      </div>
    </nav>
  );
}
