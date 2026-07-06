import { useEffect, useState } from 'react';

/**
 * Drop-in <img> replacement that degrades gracefully. When the src is
 * missing or fails to load (e.g. a 403/404 from the CDN), it renders a
 * clean "görsel yok" placeholder instead of the browser's broken-image
 * icon. The failed state resets whenever src changes so the component is
 * safe to reuse across list items and route changes.
 */
export default function SafeImage({ src, alt = '', className = '', ...rest }) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [src]);

  if (!src || failed) {
    return (
      <div className={`grid place-items-center bg-gray-100 text-[11px] uppercase tracking-wide text-gray-400 ${className}`}>
        görsel yok
      </div>
    );
  }

  return (
    <img src={src} alt={alt} className={className} onError={() => setFailed(true)} {...rest} />
  );
}
