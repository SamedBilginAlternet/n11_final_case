import { features } from '../../data/features.js';

export default function FeatureStrip() {
  return (
    <section className="rounded-lg bg-white p-5 ring-1 ring-gray-200">
      <ul className="flex flex-wrap items-center justify-between gap-6">
        {features.map((f) => (
          <li key={f.id} className="flex items-center gap-3">
            <span className="grid h-12 w-12 place-items-center rounded-full bg-n11-pinkSoft text-xl">
              {f.icon}
            </span>
            <div className="leading-tight">
              <p className="text-sm font-semibold text-n11-black">{f.title}</p>
              <p className="text-xs text-gray-500">{f.subtitle}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
