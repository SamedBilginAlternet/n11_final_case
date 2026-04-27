import { useEffect, useState } from 'react';

function compute(target) {
  const diff = Math.max(0, new Date(target).getTime() - Date.now());
  const totalSeconds = Math.floor(diff / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return { hours, minutes, seconds, expired: diff === 0 };
}

export default function CountdownTimer({ endsAt, accent = 'pink' }) {
  const [time, setTime] = useState(() => compute(endsAt));

  useEffect(() => {
    if (time.expired) return undefined;
    const id = setInterval(() => setTime(compute(endsAt)), 1000);
    return () => clearInterval(id);
  }, [endsAt, time.expired]);

  const cells = [
    { label: 'Saat',    value: time.hours },
    { label: 'Dakika',  value: time.minutes },
    { label: 'Saniye',  value: time.seconds },
  ];

  const accentRing = accent === 'pink' ? 'border-n11-pink text-n11-pinkDark' : 'border-white text-white';

  return (
    <div className="flex items-center gap-2" aria-label="Geri sayım">
      {cells.map((cell, idx) => (
        <span key={cell.label} className="flex items-center gap-2">
          <span className={`grid h-12 w-14 place-items-center rounded-md border-2 bg-white text-lg font-bold tabular-nums ${accentRing}`}>
            {String(cell.value).padStart(2, '0')}
          </span>
          {idx < cells.length - 1 && <span className="text-lg font-bold text-white">:</span>}
        </span>
      ))}
    </div>
  );
}
