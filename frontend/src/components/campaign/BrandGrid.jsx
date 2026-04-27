import { homeBrands } from '../../data/brands.js';

export default function BrandGrid() {
  return (
    <div className="grid grid-cols-3 gap-3 grid-rows-2">
      {homeBrands.map((brand) => (
        <div
          key={brand.id}
          className="flex aspect-square items-center justify-center rounded-md bg-white text-sm font-bold uppercase tracking-wider text-n11-black shadow-soft"
          aria-label={brand.name}
        >
          <span className="text-center text-xs">{brand.name}</span>
        </div>
      ))}
    </div>
  );
}
