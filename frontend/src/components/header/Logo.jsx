import { Link } from 'react-router-dom';

export default function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2 md:gap-3">
      <div className="grid h-10 w-10 place-items-center rounded-full bg-n11-pink text-white shadow-soft md:h-12 md:w-12">
        <span className="text-sm font-extrabold tracking-tight md:text-base">n11</span>
      </div>
      <span className="hidden text-sm font-semibold uppercase tracking-wider text-gray-500 md:inline">
        case
      </span>
    </Link>
  );
}
