import { useEffect, useRef, useState } from 'react';
import { useChatbot } from '../../state/ChatbotContext.jsx';

const SUGGESTIONS = [
  'Kargo ne kadar sürer?',
  'İade nasıl yapılır?',
  '500 TL altı kulaklık öner',
];

export default function ChatPanel() {
  const { open, messages, pending, send } = useChatbot();
  const [input, setInput] = useState('');
  const scrollRef = useRef(null);

  useEffect(() => {
    if (!scrollRef.current) return;
    scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [messages, pending, open]);

  if (!open) return null;

  function onSubmit(e) {
    e.preventDefault();
    if (!input.trim()) return;
    send(input);
    setInput('');
  }

  return (
    <div className="fixed bottom-24 right-6 z-40 flex h-[480px] w-80 flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-2xl md:w-96">
      <Header />

      <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto bg-gray-50 p-4">
        {messages.length === 0 && <Welcome onPick={(text) => send(text)} />}
        {messages.map((m) => (
          <Bubble key={m.id} role={m.role} text={m.content} error={m.error} />
        ))}
        {pending && <Bubble role="ASSISTANT" text="…" pending />}
      </div>

      <form onSubmit={onSubmit} className="border-t border-gray-200 bg-white p-3">
        <div className="flex items-center gap-2">
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Bir şey sor: kampanya, kargo, ürün önerisi…"
            className="input flex-1"
            disabled={pending}
          />
          <button type="submit" className="btn-primary" disabled={pending || !input.trim()}>
            Gönder
          </button>
        </div>
      </form>
    </div>
  );
}

function Header() {
  return (
    <div className="flex items-center gap-3 bg-n11-pink p-4 text-white">
      <div className="grid h-9 w-9 place-items-center rounded-full bg-white/20">
        <span aria-hidden>🤖</span>
      </div>
      <div className="leading-tight">
        <p className="text-sm font-bold">n11 Asistan</p>
        <p className="text-[11px] opacity-90">Genelde dakikalar içinde yanıtlar</p>
      </div>
    </div>
  );
}

function Welcome({ onPick }) {
  return (
    <div className="space-y-3 rounded-lg bg-white p-3 text-sm text-gray-700 shadow-soft">
      <p>Merhaba! Sana nasıl yardımcı olabilirim?</p>
      <div className="flex flex-wrap gap-2">
        {SUGGESTIONS.map((s) => (
          <button
            key={s}
            onClick={() => onPick(s)}
            className="rounded-full border border-n11-pinkSoft bg-n11-pinkBg px-3 py-1 text-xs font-medium text-n11-pinkDark hover:bg-n11-pinkSoft"
          >
            {s}
          </button>
        ))}
      </div>
    </div>
  );
}

function Bubble({ role, text, error, pending }) {
  const isUser = role === 'USER';
  const base = 'max-w-[80%] rounded-2xl px-3 py-2 text-sm leading-snug whitespace-pre-line';
  const tone = isUser
    ? 'bg-n11-pink text-white rounded-br-sm self-end'
    : `bg-white shadow-soft text-gray-800 rounded-bl-sm self-start ${error ? 'ring-1 ring-red-200' : ''}`;
  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div className={`${base} ${tone} ${pending ? 'opacity-60' : ''}`}>{text}</div>
    </div>
  );
}
