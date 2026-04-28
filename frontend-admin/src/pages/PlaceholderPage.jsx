/**
 * Single placeholder for pages not yet built — Products + Coupons.
 * Replaced by real implementations in their own commits.
 */
export default function PlaceholderPage({ title, description }) {
  return (
    <div className="space-y-3">
      <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
      <p className="text-sm text-slate-500">{description}</p>
      <div className="card grid place-items-center p-12 text-sm text-slate-400">
        Bu sayfa yakında gelecek.
      </div>
    </div>
  );
}
