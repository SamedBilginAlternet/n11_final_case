import { Link } from 'react-router-dom';

export default function FooterLinkColumn({ column }) {
  return (
    <div>
      <h4 className="mb-3 text-sm font-semibold uppercase tracking-wide text-n11-black">{column.title}</h4>
      <ul className="space-y-2">
        {column.links.map((link) => (
          <li key={link.label}>
            <Link to={link.to} className="text-sm text-gray-600 hover:text-n11-pink">
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
