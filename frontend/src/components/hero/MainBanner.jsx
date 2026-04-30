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
        className="relative h-56 overflow-hidden rounded-xl bg-cover bg-center sm:h-72 sm:rounded-2xl md:h-80"
        style={{ backgroundImage: `url(${banner.bgImage}), ${banner.bgFallback}` }}
      >
        <span className="absolute left-3 top-3 rounded-md bg-n11-black px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-white sm:left-5 sm:top-5 sm:px-3 sm:py-1.5 sm:text-xs">
          {banner.badge}
        </span>

        <div className="absolute right-4 top-1/2 max-w-[60%] -translate-y-1/2 text-right sm:right-6 sm:max-w-md md:right-12">
          {banner.headlineLines.map((line, idx) => (
            <p
              key={idx}
              className={`leading-tight ${idx === 0 ? 'text-xl font-bold text-n11-black sm:text-3xl md:text-4xl' : 'text-2xl font-extrabold text-n11-black sm:text-4xl md:text-5xl'}`}
            >
              {line}
            </p>
          ))}
          <p className="mt-2 inline-block bg-n11-pinkSoft px-2 py-1 text-sm font-semibold text-n11-pinkDark sm:mt-3 sm:px-3 sm:text-base md:text-lg">
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
