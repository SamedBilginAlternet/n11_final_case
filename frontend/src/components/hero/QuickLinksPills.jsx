import { Link } from 'react-router-dom';
import { quickLinks } from '../../data/quickLinks.js';

export default function QuickLinksPills() {
  return (
    <nav className="flex justify-center">
      <ul className="flex items-center divide-x divide-gray-300 rounded-full bg-gray-100 px-2">
        {quickLinks.map((link) => (
          <li key={link.id} className="px-4 py-2">
            <Link to={link.to} className="pill-link font-medium">
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
