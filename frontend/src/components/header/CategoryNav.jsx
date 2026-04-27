import { Link } from 'react-router-dom';
import { navCategories } from '../../data/navCategories.js';

export default function CategoryNav() {
  return (
    <nav className="border-b border-gray-200 bg-white">
      <ul className="mx-auto flex max-w-7xl items-stretch justify-between gap-1 overflow-x-auto px-4">
        {navCategories.map(({ slug, label, Icon }) => (
          <li key={slug} className="flex-shrink-0">
            <Link
              to={slug === 'super-firsatlar' ? '/catalog' : `/catalog?category=${slug}`}
              className="group flex w-24 flex-col items-center gap-1.5 px-2 py-3 text-center transition-colors hover:bg-n11-pinkBg"
            >
              <Icon
                size={22}
                strokeWidth={1.7}
                className="text-gray-700 transition-colors group-hover:text-n11-pink"
                aria-hidden
              />
              <span className="text-[12px] font-medium text-gray-700 group-hover:text-n11-pinkDark">
                {label}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
