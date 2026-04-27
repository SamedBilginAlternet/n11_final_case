import { AnimatePresence, motion } from 'framer-motion';
import { Bot, Sparkles, X } from 'lucide-react';
import { useChatbot } from '../../state/ChatbotContext.jsx';

export default function ChatBubbleButton() {
  const { open, setOpen, messages } = useChatbot();
  const unread = messages.filter((m) => m.role === 'ASSISTANT').length > 0 && !open;

  return (
    <motion.button
      type="button"
      onClick={() => setOpen(!open)}
      aria-label={open ? 'Asistanı kapat' : 'Asistanı aç'}
      initial={{ scale: 0, rotate: -90, opacity: 0 }}
      animate={{ scale: 1, rotate: 0, opacity: 1 }}
      transition={{ type: 'spring', stiffness: 260, damping: 20, delay: 0.4 }}
      whileHover={{ scale: 1.08 }}
      whileTap={{ scale: 0.92 }}
      className="group fixed bottom-6 right-6 z-40 grid h-16 w-16 place-items-center rounded-full bg-gradient-to-br from-n11-pink via-fuchsia-500 to-purple-600 text-white shadow-xl shadow-pink-400/40 outline-none ring-0 focus-visible:ring-4 focus-visible:ring-pink-300/50"
    >
      {/* Outer breathing pulse — only when closed, hints "I'm alive" */}
      {!open && (
        <motion.span
          className="absolute inset-0 rounded-full bg-n11-pink/40"
          animate={{ scale: [1, 1.35, 1], opacity: [0.6, 0, 0.6] }}
          transition={{ duration: 2.2, repeat: Infinity, ease: 'easeInOut' }}
          aria-hidden
        />
      )}

      {/* Sparkle hint — top-right, twinkles to indicate AI assistant */}
      {!open && (
        <motion.span
          className="absolute -right-1 -top-1 text-amber-300"
          animate={{ rotate: [0, 20, -10, 0], scale: [1, 1.2, 1] }}
          transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
          aria-hidden
        >
          <Sparkles className="h-4 w-4 drop-shadow" strokeWidth={2.5} fill="currentColor" />
        </motion.span>
      )}

      {/* Icon swap with rotation */}
      <AnimatePresence mode="wait" initial={false}>
        {open ? (
          <motion.span
            key="close"
            initial={{ rotate: -90, opacity: 0, scale: 0.6 }}
            animate={{ rotate: 0, opacity: 1, scale: 1 }}
            exit={{ rotate: 90, opacity: 0, scale: 0.6 }}
            transition={{ duration: 0.18 }}
            className="relative z-10"
          >
            <X className="h-7 w-7" strokeWidth={2.5} />
          </motion.span>
        ) : (
          <motion.span
            key="bot"
            initial={{ rotate: 90, opacity: 0, scale: 0.6 }}
            animate={{ rotate: 0, opacity: 1, scale: 1 }}
            exit={{ rotate: -90, opacity: 0, scale: 0.6 }}
            transition={{ duration: 0.18 }}
            className="relative z-10"
          >
            <motion.span
              animate={{ y: [0, -2, 0] }}
              transition={{ duration: 2.6, repeat: Infinity, ease: 'easeInOut' }}
              className="inline-block"
            >
              <Bot className="h-7 w-7" strokeWidth={2.25} />
            </motion.span>
          </motion.span>
        )}
      </AnimatePresence>

      {/* Unread dot */}
      <AnimatePresence>
        {unread && (
          <motion.span
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            exit={{ scale: 0 }}
            transition={{ type: 'spring', stiffness: 400, damping: 18 }}
            className="absolute right-0 top-0 h-3.5 w-3.5 rounded-full bg-amber-400 ring-2 ring-white"
            aria-hidden
          >
            <motion.span
              className="absolute inset-0 rounded-full bg-amber-400"
              animate={{ scale: [1, 1.8], opacity: [0.7, 0] }}
              transition={{ duration: 1.4, repeat: Infinity, ease: 'easeOut' }}
            />
          </motion.span>
        )}
      </AnimatePresence>
    </motion.button>
  );
}
