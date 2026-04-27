import { useEffect, useState } from 'react';
import { heroBanners } from '../../data/banners.js';
import CarouselDots from './CarouselDots.jsx';

const ROTATE_MS = 6000;

export default function MainBanner() {
  const [active, setActive] = useState(0);

  useEffect(() => {
    const id = setInterval(() => {
      setActive((i) => (i + 1) % heroBanners.length);
    }, ROTATE_MS);
    return () => clearInterval(id);
  }, []);

  const banner = heroBanners[active];

  return (
    <div className="relative">
      <div
        className="relative h-72 overflow-hidden rounded-2xl bg-cover bg-center md:h-80"
        style={{ backgroundImage: `url(${banner.bgImage}), ${banner.bgFallback}` }}
      >
        <span className="absolute left-5 top-5 rounded-md bg-n11-black px-3 py-1.5 text-xs font-bold uppercase tracking-wider text-white">
          {banner.badge}
        </span>

        <div className="absolute right-6 top-1/2 max-w-md -translate-y-1/2 text-right md:right-12">
          {banner.headlineLines.map((line, idx) => (
            <p
              key={idx}
              className={`leading-tight ${idx === 0 ? 'text-3xl font-bold text-n11-black md:text-4xl' : 'text-4xl font-extrabold text-n11-black md:text-5xl'}`}
            >
              {line}
            </p>
          ))}
          <p className="mt-3 inline-block bg-n11-pinkSoft px-3 py-1 text-base font-semibold text-n11-pinkDark md:text-lg">
            {banner.highlight}
          </p>
        </div>
      </div>

      <div className="mt-3 flex justify-center">
        <CarouselDots count={heroBanners.length} active={active} onSelect={setActive} />
      </div>
    </div>
  );
}
