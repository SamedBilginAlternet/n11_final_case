export default function CarouselDots({ count, active, onSelect }) {
  return (
    <div className="flex items-center gap-2" role="tablist" aria-label="Banner navigation">
      {Array.from({ length: count }).map((_, i) => {
        const isActive = i === active;
        return (
          <button
            key={i}
            role="tab"
            aria-selected={isActive}
            onClick={() => onSelect(i)}
            className={`h-2.5 rounded-full transition-all ${
              isActive ? 'w-6 bg-n11-pinkDark' : 'w-2.5 bg-gray-300 hover:bg-gray-400'
            }`}
          />
        );
      })}
    </div>
  );
}
