import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { api } from '../api/client.js';

const STORAGE_KEY = 'n11.chat.sessionId';
const ChatbotContext = createContext(null);

export function ChatbotProvider({ children }) {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [pending, setPending] = useState(false);
  const sessionRef = useRef(localStorage.getItem(STORAGE_KEY));

  useEffect(() => {
    if (!sessionRef.current) return;
    api
      .get(`/api/chat/${sessionRef.current}/history`)
      .then((res) => {
        setMessages(res.data.map(toUiMessage));
      })
      .catch(() => {
        sessionRef.current = null;
        localStorage.removeItem(STORAGE_KEY);
      });
  }, []);

  const send = useCallback(async (text) => {
    const trimmed = text.trim();
    if (!trimmed) return;
    setMessages((prev) => [...prev, { id: `u-${Date.now()}`, role: 'USER', content: trimmed }]);
    setPending(true);
    try {
      const { data } = await api.post('/api/chat', {
        sessionId: sessionRef.current,
        message: trimmed,
      });
      sessionRef.current = data.sessionId;
      localStorage.setItem(STORAGE_KEY, data.sessionId);
      setMessages((prev) => [...prev, { id: `a-${Date.now()}`, role: 'ASSISTANT', content: data.reply }]);
    } catch (err) {
      setMessages((prev) => [...prev, {
        id: `e-${Date.now()}`,
        role: 'ASSISTANT',
        content: 'Üzgünüm, asistan şu anda yanıt veremiyor.',
        error: true,
      }]);
    } finally {
      setPending(false);
    }
  }, []);

  return (
    <ChatbotContext.Provider value={{ open, setOpen, messages, pending, send }}>
      {children}
    </ChatbotContext.Provider>
  );
}

function toUiMessage(m) {
  return { id: m.id, role: m.role, content: m.content };
}

export function useChatbot() {
  const ctx = useContext(ChatbotContext);
  if (!ctx) throw new Error('useChatbot must be used inside ChatbotProvider');
  return ctx;
}
