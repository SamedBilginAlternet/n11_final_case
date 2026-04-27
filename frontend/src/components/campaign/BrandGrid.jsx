import { useState } from 'react';
import { homeBrands } from '../../data/brands.js';

export default function BrandGrid() {
  return (
    <div className="grid grid-cols-3 grid-rows-2 gap-3">
      {homeBrands.map((brand) => (
        <BrandTile key={brand.id} brand={brand} />
      ))}
    </div>
  );
}

function BrandTile({ brand }) {
  // 0 = clearbit (best), 1 = google favicon (fallback), 2 = initials text
  const [stage, setStage] = useState(0);
  const src =
    stage === 0
      ? `https://logo.clearbit.com/${brand.domain}`
      : stage === 1
      ? `https://www.google.com/s2/favicons?domain=${brand.domain}&sz=128`
      : null;

  return (
    <div
      className="flex aspect-square items-center justify-center rounded-md bg-white p-3 shadow-soft"
      aria-label={brand.name}
      title={brand.name}
    >
      {src ? (
        <img
          src={src}
          alt={brand.name}
          loading="lazy"
          className="max-h-full max-w-full object-contain"
          onError={() => setStage((s) => s + 1)}
        />
      ) : (
        <span className="text-base font-extrabold tracking-wider text-n11-pinkDark">
          {brand.initials}
        </span>
      )}
    </div>
  );
}
