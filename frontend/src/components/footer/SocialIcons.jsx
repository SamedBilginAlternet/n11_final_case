import { socialMedia } from '../../data/footer.js';

export default function SocialIcons() {
  return (
    <div className="flex items-center gap-3">
      <span className="text-sm font-semibold text-n11-black">Bizi Takip Edin</span>
      <ul className="flex items-center gap-2">
        {socialMedia.map((s) => (
          <li key={s.id}>
            <a
              href="#"
              aria-label={s.label}
              className="grid h-9 w-9 place-items-center rounded-full bg-gray-100 text-sm font-semibold text-gray-700 hover:bg-n11-pinkBg hover:text-n11-pink"
            >
              {s.icon}
            </a>
          </li>
        ))}
        <li>
          <a
            href="#"
            className="grid h-9 min-w-[64px] place-items-center rounded-full bg-gray-100 px-3 text-xs font-bold uppercase tracking-wider text-gray-700 hover:bg-n11-pinkBg hover:text-n11-pink"
          >
            Blog
          </a>
        </li>
      </ul>
    </div>
  );
}
