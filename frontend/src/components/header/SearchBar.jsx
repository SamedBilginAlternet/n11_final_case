import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api/client.js';

export default function SearchBar() {
  const [value, setValue] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const debounceRef = useRef(null);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!value || value.trim().length < 2) {
      setSuggestions([]);
      return;
    }
    debounceRef.current = setTimeout(() => {
      api
        .get(`/api/products/autocomplete?q=${encodeURIComponent(value.trim())}&limit=6`)
        .then((res) => setSuggestions(res.data))
        .catch(() => setSuggestions([]));
    }, 220);
    return () => clearTimeout(debounceRef.current);
  }, [value]);

  function onSubmit(e) {
    e.preventDefault();
    if (!value.trim()) return;
    setOpen(false);
    navigate(`/catalog?q=${encodeURIComponent(value.trim())}`);
  }

  return (
    <form onSubmit={onSubmit} className="relative flex-1">
      <div className="flex items-center rounded-full bg-gray-100 px-4 py-2 ring-1 ring-transparent focus-within:ring-n11-pink">
        <SearchIcon className="h-5 w-5 text-gray-400" />
        <input
          type="search"
          value={value}
          onChange={(e) => {
            setValue(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          placeholder="Ürün, kategori, marka ara"
          className="ml-2 w-full bg-transparent text-sm text-gray-800 placeholder:text-gray-400 focus:outline-none"
        />
      </div>

      {open && suggestions.length > 0 && (
        <ul className="absolute z-20 mt-2 max-h-80 w-full overflow-auto rounded-md border border-gray-200 bg-white shadow-soft">
          {suggestions.map((s) => (
            <li key={s.id}>
              <Link
                to={`/products/${s.slug}`}
                onClick={() => setOpen(false)}
                className="flex items-center gap-3 px-3 py-2 hover:bg-n11-pinkBg"
              >
                {s.imageUrl ? (
                  <img src={s.imageUrl} alt="" className="h-10 w-10 rounded object-cover" />
                ) : (
                  <div className="h-10 w-10 rounded bg-gray-100" />
                )}
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-gray-800">{s.name}</p>
                  <p className="truncate text-xs text-gray-500">{s.categoryName}</p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </form>
  );
}

function SearchIcon({ className }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <circle cx="11" cy="11" r="7" />
      <path strokeLinecap="round" d="m20 20-3.5-3.5" />
    </svg>
  );
}
