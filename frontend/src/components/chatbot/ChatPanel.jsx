import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Bot, Send } from 'lucide-react';
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

  function onSubmit(e) {
    e.preventDefault();
    if (!input.trim()) return;
    send(input);
    setInput('');
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0, y: 24, scale: 0.92 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 24, scale: 0.92 }}
          transition={{ type: 'spring', stiffness: 320, damping: 26 }}
          className="fixed bottom-24 right-3 z-40 flex h-[min(480px,calc(100vh-160px))] w-[calc(100vw-24px)] max-w-sm origin-bottom-right flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-2xl sm:right-6 md:w-96 md:max-w-none"
        >
          <Header />

          <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto bg-gray-50 p-4">
            {messages.length === 0 && <Welcome onPick={(text) => send(text)} />}
            <AnimatePresence initial={false}>
              {messages.map((m) => (
                <Bubble key={m.id} role={m.role} text={m.content} error={m.error} />
              ))}
            </AnimatePresence>
            {pending && <TypingDots />}
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
              <motion.button
                type="submit"
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="btn-primary flex items-center gap-1.5"
                disabled={pending || !input.trim()}
              >
                <Send className="h-4 w-4" strokeWidth={2.5} />
                <span>Gönder</span>
              </motion.button>
            </div>
          </form>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function Header() {
  return (
    <div className="relative flex items-center gap-3 bg-gradient-to-br from-n11-pink via-fuchsia-500 to-purple-600 p-4 text-white">
      <motion.div
        className="grid h-10 w-10 place-items-center rounded-full bg-white/20 backdrop-blur"
        animate={{ y: [0, -2, 0] }}
        transition={{ duration: 2.6, repeat: Infinity, ease: 'easeInOut' }}
      >
        <Bot className="h-5 w-5" strokeWidth={2.25} />
      </motion.div>
      <div className="leading-tight">
        <p className="text-sm font-bold">n11 Asistan</p>
        <p className="flex items-center gap-1.5 text-[11px] opacity-90">
          <span className="relative flex h-1.5 w-1.5">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-300 opacity-75" />
            <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-emerald-300" />
          </span>
          Çevrimiçi · genelde dakikalar içinde yanıtlar
        </p>
      </div>
    </div>
  );
}

function Welcome({ onPick }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
      className="space-y-3 rounded-lg bg-white p-3 text-sm text-gray-700 shadow-soft"
    >
      <p>Merhaba! Sana nasıl yardımcı olabilirim?</p>
      <div className="flex flex-wrap gap-2">
        {SUGGESTIONS.map((s, i) => (
          <motion.button
            key={s}
            type="button"
            onClick={() => onPick(s)}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 + i * 0.07 }}
            whileHover={{ scale: 1.04 }}
            whileTap={{ scale: 0.96 }}
            className="rounded-full border border-n11-pinkSoft bg-n11-pinkBg px-3 py-1 text-xs font-medium text-n11-pinkDark hover:bg-n11-pinkSoft"
          >
            {s}
          </motion.button>
        ))}
      </div>
    </motion.div>
  );
}

function Bubble({ role, text, error }) {
  const isUser = role === 'USER';
  const base = 'max-w-[80%] rounded-2xl px-3 py-2 text-sm leading-snug whitespace-pre-line';
  const tone = isUser
    ? 'bg-n11-pink text-white rounded-br-sm self-end'
    : `bg-white shadow-soft text-gray-800 rounded-bl-sm self-start ${error ? 'ring-1 ring-red-200' : ''}`;
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 8, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ type: 'spring', stiffness: 380, damping: 28 }}
      className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}
    >
      <div className={`${base} ${tone}`}>{text}</div>
    </motion.div>
  );
}

function TypingDots() {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className="flex justify-start"
    >
      <div className="flex items-center gap-1 rounded-2xl rounded-bl-sm bg-white px-3 py-2.5 shadow-soft">
        {[0, 1, 2].map((i) => (
          <motion.span
            key={i}
            className="block h-1.5 w-1.5 rounded-full bg-n11-pink"
            animate={{ y: [0, -4, 0], opacity: [0.4, 1, 0.4] }}
            transition={{ duration: 0.9, repeat: Infinity, delay: i * 0.15, ease: 'easeInOut' }}
          />
        ))}
      </div>
    </motion.div>
  );
}
