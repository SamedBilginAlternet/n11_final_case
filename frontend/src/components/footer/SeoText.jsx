import { useState } from 'react';
import { seoText } from '../../data/footer.js';

export default function SeoText() {
  const [expanded, setExpanded] = useState(false);

  return (
    <section className="rounded-lg bg-white p-5 ring-1 ring-gray-200">
      <h3 className="text-lg font-semibold text-n11-black">{seoText.title}</h3>
      <p className={`mt-2 text-sm leading-relaxed text-gray-500 ${expanded ? '' : 'line-clamp-3'}`}>
        {renderBold(seoText.body)}
      </p>
      <button
        onClick={() => setExpanded((e) => !e)}
        className="mt-2 text-sm font-medium text-n11-pink hover:text-n11-pinkDark"
      >
        {expanded ? 'Daha Az Göster' : 'Devamını Göster…'}
      </button>
    </section>
  );
}

function renderBold(text) {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, idx) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return (
        <strong key={idx} className="font-semibold text-n11-black">
          {part.slice(2, -2)}
        </strong>
      );
    }
    return <span key={idx}>{part}</span>;
  });
}
