import { Link } from 'react-router-dom';
import { storesCard } from '../../data/footer.js';

export default function StoresCard() {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <h4 className="text-sm font-semibold uppercase tracking-wide text-n11-black">{storesCard.title}</h4>
      <p className="mt-1 text-xs text-gray-500">{storesCard.description}</p>

      <div className="mt-3 space-y-2">
        <Link to={storesCard.primary.to} className="btn-outline w-full">
          {storesCard.primary.label}
        </Link>
        <Link to={storesCard.secondary.to} className="btn-dark w-full">
          {storesCard.secondary.label}
        </Link>
      </div>

      <ul className="mt-4 space-y-1">
        {storesCard.links.map((l) => (
          <li key={l.label}>
            <Link to={l.to} className="text-xs text-gray-500 hover:text-n11-pink">
              {l.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
