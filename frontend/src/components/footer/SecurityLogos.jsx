import { securityLogos } from '../../data/footer.js';

export default function SecurityLogos() {
  return (
    <div className="flex items-center gap-3">
      {securityLogos.map((logo) => (
        <div
          key={logo.id}
          className="flex items-center gap-2 rounded-md border border-gray-200 bg-white px-3 py-2"
        >
          <div className="grid h-10 w-10 place-items-center rounded bg-gray-100 text-[10px] font-bold uppercase text-gray-700">
            {logo.id === 'etbis' ? <QrIcon /> : <ShieldIcon />}
          </div>
          <div className="leading-tight">
            <p className="text-xs font-bold text-n11-black">{logo.label}</p>
            <p className="text-[10px] text-gray-500">{logo.subtitle}</p>
          </div>
        </div>
      ))}
    </div>
  );
}

function QrIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className="h-6 w-6 text-n11-black">
      <path d="M3 3h6v6H3V3Zm2 2v2h2V5H5Zm10-2h6v6h-6V3Zm2 2v2h2V5h-2ZM3 15h6v6H3v-6Zm2 2v2h2v-2H5Zm10 0h2v2h-2v-2Zm-4 0h2v2h-2v-2Zm4-4h2v2h-2v-2Zm4 4h2v2h-2v-2Zm0-4h2v2h-2v-2Zm-8 0h2v2h-2v-2Zm0 8h2v2h-2v-2Z" />
    </svg>
  );
}

function ShieldIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="h-6 w-6 text-n11-pink">
      <path d="M12 2 4 5v7c0 5 3.5 8.5 8 10 4.5-1.5 8-5 8-10V5L12 2Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}
