import { appBadges } from '../../data/footer.js';

export default function AppBadges() {
  return (
    <div className="flex items-center gap-3">
      {appBadges.map((badge) => (
        <a
          key={badge.id}
          href="#"
          className="flex items-center gap-2 rounded-md border border-gray-300 bg-white px-3 py-2 text-left hover:bg-gray-50"
        >
          <span className="grid h-8 w-8 place-items-center rounded bg-n11-black text-white text-xs font-bold">
            {badge.id === 'app-store' ? '' : '▶'}
          </span>
          <div className="leading-tight">
            <p className="text-[10px] uppercase text-gray-400">{badge.sub}</p>
            <p className="text-xs font-semibold text-n11-black">{badge.label}</p>
          </div>
        </a>
      ))}
    </div>
  );
}
