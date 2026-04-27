import { dailyNews } from '../../data/banners.js';
import CountdownTimer from './CountdownTimer.jsx';

export default function NewsBar() {
  const { Icon, text, endsAt } = dailyNews;
  return (
    <div className="overflow-hidden rounded-xl bg-pink-gradient">
      <div className="flex flex-col items-center justify-between gap-3 px-5 py-4 md:flex-row">
        <div className="flex items-center gap-3 text-white">
          <span className="grid h-10 w-10 place-items-center rounded-full bg-white/20 backdrop-blur-sm">
            <Icon size={22} strokeWidth={1.8} aria-hidden />
          </span>
          <p className="text-sm font-semibold drop-shadow md:text-base">{text}</p>
        </div>
        <CountdownTimer endsAt={endsAt} />
      </div>
    </div>
  );
}
