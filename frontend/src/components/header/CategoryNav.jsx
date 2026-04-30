import { Link } from 'react-router-dom';
import { navCategories } from '../../data/navCategories.js';

/**
 * Horizontal category strip. The default scroll behaviour is fine across
 * widths, but on mobile we tighten widths/gaps so 4-5 chips fit without
 * forcing the user into the overflow scroll for every browse session.
 */
export default function CategoryNav() {
  return (
    <nav className="border-b border-gray-200 bg-white">
      <ul className="mx-auto flex max-w-7xl items-stretch gap-1 overflow-x-auto px-2 md:justify-between md:px-4">
        {navCategories.map(({ slug, label, Icon }) => (
          <li key={slug} className="flex-shrink-0">
            <Link
              to={slug === 'super-firsatlar' ? '/catalog' : `/catalog?category=${slug}`}
              className="group flex w-[68px] flex-col items-center gap-1 px-1 py-2 text-center transition-colors hover:bg-n11-pinkBg md:w-24 md:gap-1.5 md:px-2 md:py-3"
            >
              <Icon
                size={20}
                strokeWidth={1.7}
                className="text-gray-700 transition-colors group-hover:text-n11-pink md:[&]:size-[22px]"
                aria-hidden
              />
              <span className="text-[11px] font-medium leading-tight text-gray-700 group-hover:text-n11-pinkDark md:text-[12px]">
                {label}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
