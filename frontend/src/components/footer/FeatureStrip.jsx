import { features } from '../../data/features.js';

export default function FeatureStrip() {
  return (
    <section className="rounded-lg bg-white p-4 ring-1 ring-gray-200 sm:p-5">
      <ul className="grid grid-cols-2 gap-3 sm:flex sm:flex-wrap sm:items-center sm:justify-between sm:gap-6">
        {features.map(({ id, Icon, title, subtitle }) => (
          <li key={id} className="flex items-center gap-2.5 sm:gap-3">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-n11-pinkSoft text-n11-pink sm:h-12 sm:w-12">
              <Icon size={20} strokeWidth={1.7} aria-hidden className="sm:[&]:size-[22px]" />
            </span>
            <div className="min-w-0 leading-tight">
              <p className="truncate text-xs font-semibold text-n11-black sm:text-sm">{title}</p>
              <p className="truncate text-[11px] text-gray-500 sm:text-xs">{subtitle}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
