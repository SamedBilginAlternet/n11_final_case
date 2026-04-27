import { Link } from 'react-router-dom';
import { navCategories } from '../../data/navCategories.js';

export default function CategoryNav() {
  return (
    <nav className="border-b border-gray-200 bg-white">
      <ul className="mx-auto flex max-w-7xl items-stretch justify-between gap-1 overflow-x-auto px-4">
        {navCategories.map((cat) => (
          <li key={cat.slug} className="flex-shrink-0">
            <Link
              to={cat.slug === 'super-firsatlar' ? '/catalog' : `/catalog?category=${cat.slug}`}
              className="flex w-24 flex-col items-center gap-1 px-2 py-3 text-center transition-colors hover:bg-n11-pinkBg"
            >
              <span className="text-2xl leading-none" aria-hidden>{cat.emoji}</span>
              <span className="text-[12px] font-medium text-gray-700">{cat.label}</span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
