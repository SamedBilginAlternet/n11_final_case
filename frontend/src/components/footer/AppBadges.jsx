import { AppleIcon, GooglePlayIcon } from '../icons/BrandIcons.jsx';

const BADGES = [
  {
    id: 'app-store',
    href: 'https://apps.apple.com',
    Icon: AppleIcon,
    line1: 'Download on the',
    line2: 'App Store',
  },
  {
    id: 'google-play',
    href: 'https://play.google.com',
    Icon: GooglePlayIcon,
    line1: 'GET IT ON',
    line2: 'Google Play',
  },
];

export default function AppBadges() {
  return (
    <div className="flex items-center gap-3">
      {BADGES.map(({ id, href, Icon, line1, line2 }) => (
        <a
          key={id}
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={line2}
          className="flex items-center gap-2 rounded-md bg-n11-black px-3 py-2 text-white transition-opacity hover:opacity-90"
        >
          <Icon width={22} height={22} />
          <div className="leading-tight">
            <p className="text-[9px] uppercase tracking-wider opacity-80">{line1}</p>
            <p className="text-sm font-semibold">{line2}</p>
          </div>
        </a>
      ))}
    </div>
  );
}
