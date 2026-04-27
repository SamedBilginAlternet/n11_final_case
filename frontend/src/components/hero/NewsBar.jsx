import { dailyNews } from '../../data/banners.js';
import CountdownTimer from './CountdownTimer.jsx';

export default function NewsBar() {
  return (
    <div className="overflow-hidden rounded-xl bg-pink-gradient">
      <div className="flex flex-col items-center justify-between gap-3 px-5 py-4 md:flex-row">
        <div className="flex items-center gap-3 text-white">
          <span className="text-3xl" aria-hidden>{dailyNews.emoji}</span>
          <p className="text-sm font-semibold drop-shadow md:text-base">{dailyNews.text}</p>
        </div>
        <CountdownTimer endsAt={dailyNews.endsAt} />
      </div>
    </div>
  );
}
