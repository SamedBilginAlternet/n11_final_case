import { Link } from 'react-router-dom';

export default function Logo() {
  return (
    <Link to="/" className="flex items-center gap-3">
      <div className="grid h-12 w-12 place-items-center rounded-full bg-n11-pink text-white shadow-soft">
        <span className="text-base font-extrabold tracking-tight">n11</span>
      </div>
      <span className="hidden text-sm font-semibold uppercase tracking-wider text-gray-500 md:inline">
        case
      </span>
    </Link>
  );
}
