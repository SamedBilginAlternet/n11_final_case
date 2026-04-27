import {
  InstagramIcon,
  FacebookIcon,
  XIcon,
  YouTubeIcon,
  RssIcon,
} from '../icons/BrandIcons.jsx';

const SOCIAL = [
  { id: 'instagram', label: 'Instagram', href: 'https://instagram.com',         Icon: InstagramIcon, brand: 'hover:bg-[#E1306C]' },
  { id: 'facebook',  label: 'Facebook',  href: 'https://facebook.com',          Icon: FacebookIcon,  brand: 'hover:bg-[#1877F2]' },
  { id: 'twitter',   label: 'X (Twitter)', href: 'https://x.com',               Icon: XIcon,         brand: 'hover:bg-black' },
  { id: 'youtube',   label: 'YouTube',   href: 'https://youtube.com',           Icon: YouTubeIcon,   brand: 'hover:bg-[#FF0000]' },
  { id: 'blog',      label: 'Blog',      href: '/blog',                         Icon: RssIcon,       brand: 'hover:bg-[#FF6600]' },
];

export default function SocialIcons() {
  return (
    <div className="flex items-center gap-3">
      <span className="text-sm font-semibold text-n11-black">Bizi Takip Edin</span>
      <ul className="flex items-center gap-2">
        {SOCIAL.map(({ id, label, href, Icon, brand }) => (
          <li key={id}>
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={label}
              title={label}
              className={`grid h-9 w-9 place-items-center rounded-full bg-gray-100 text-gray-700 transition-colors hover:text-white ${brand}`}
            >
              <Icon />
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
