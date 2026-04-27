import { useChatbot } from '../../state/ChatbotContext.jsx';

export default function ChatBubbleButton() {
  const { open, setOpen, messages } = useChatbot();
  const unread = messages.filter((m) => m.role === 'ASSISTANT').length > 0 && !open;

  return (
    <button
      onClick={() => setOpen(!open)}
      aria-label="Asistan"
      className="fixed bottom-6 right-6 z-40 grid h-14 w-14 place-items-center rounded-full bg-n11-pink text-white shadow-lg shadow-pink-300/40 transition-transform hover:scale-105"
    >
      {open ? <CloseIcon /> : <ChatIcon />}
      {unread && <span className="absolute -right-1 -top-1 h-3 w-3 rounded-full bg-amber-400 ring-2 ring-white" />}
    </button>
  );
}

function ChatIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="h-6 w-6">
      <path d="M21 12a8 8 0 0 1-11.5 7.2L4 21l1.8-5A8 8 0 1 1 21 12Z" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="h-6 w-6">
      <path d="m6 6 12 12M18 6 6 18" strokeLinecap="round" />
    </svg>
  );
}
