import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import ProductShelf from '../product/ProductShelf.jsx';

/**
 * One labelled shelf on the homepage — title, optional lucide icon, "tümünü
 * gör" link to the catalog, and a horizontal product rail underneath.
 *
 * Centralising this layout keeps the homepage file readable: the home page
 * itself just lists which sections to show; this component owns spacing,
 * heading sizes and the see-all affordance so they stay consistent.
 */
export default function HomeSection({
  title,
  subtitle,
  icon: Icon,
  categorySlug,
  sort,
  size = 6,
  viewAllHref,
  accent = 'default',
}) {
  const accentClass =
    accent === 'pink'
      ? 'bg-n11-pinkBg ring-1 ring-n11-pink/20'
      : accent === 'dark'
      ? 'bg-n11-black text-white'
      : 'bg-white ring-1 ring-gray-200';

  const linkColor = accent === 'dark' ? 'text-white/90 hover:text-white' : 'text-n11-pink hover:text-n11-pinkDark';
  const subtitleColor = accent === 'dark' ? 'text-white/70' : 'text-gray-500';
  const iconBadgeClass =
    accent === 'pink'
      ? 'bg-white text-n11-pink ring-1 ring-n11-pink/30'
      : accent === 'dark'
      ? 'bg-white/10 text-white ring-1 ring-white/20'
      : 'bg-n11-pinkBg text-n11-pink ring-1 ring-n11-pink/20';

  return (
    <section className={`rounded-xl p-4 md:p-5 ${accentClass}`}>
      <header className="mb-3 flex items-end justify-between gap-3 md:mb-4">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2.5 text-lg font-extrabold tracking-tight md:text-xl">
            {Icon && (
              <span
                className={`grid h-9 w-9 shrink-0 place-items-center rounded-lg ${iconBadgeClass}`}
                aria-hidden
              >
                <Icon className="h-5 w-5" strokeWidth={2} />
              </span>
            )}
            <span className="truncate">{title}</span>
          </h2>
          {subtitle && <p className={`mt-1 text-xs md:text-sm ${subtitleColor}`}>{subtitle}</p>}
        </div>
        {viewAllHref && (
          <Link
            to={viewAllHref}
            className={`flex shrink-0 items-center gap-0.5 text-xs font-semibold md:text-sm ${linkColor}`}
          >
            <span className="hidden sm:inline">Tümünü gör</span>
            <span className="sm:hidden">Tümü</span>
            <ChevronRight className="h-4 w-4" strokeWidth={2.2} />
          </Link>
        )}
      </header>
      <ProductShelf categorySlug={categorySlug} sort={sort} size={size} />
    </section>
  );
}
