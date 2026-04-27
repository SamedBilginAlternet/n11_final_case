import { Link } from 'react-router-dom';
import { quickLinks } from '../../data/quickLinks.js';

export default function QuickLinksPills() {
  return (
    <nav className="flex justify-center">
      <ul className="flex items-center divide-x divide-gray-300 rounded-full bg-gray-100 px-2">
        {quickLinks.map(({ id, label, to, Icon }) => (
          <li key={id} className="px-4 py-2">
            <Link to={to} className="pill-link flex items-center gap-1.5 font-medium">
              <Icon size={16} strokeWidth={1.7} aria-hidden />
              {label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
