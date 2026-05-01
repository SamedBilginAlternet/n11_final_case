import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import App from './App.jsx';
import { AuthProvider } from './state/AuthContext.jsx';
import { CartProvider } from './state/CartContext.jsx';
import { ChatbotProvider } from './state/ChatbotContext.jsx';
import { WishlistProvider } from './state/WishlistContext.jsx';
import { Sentry, initSentry } from './lib/sentry.js';
import './styles/index.css';

// Init before React mounts so the ErrorBoundary registers correctly.
initSentry();

// Sentry.ErrorBoundary catches render-time errors React doesn't propagate
// to window.onerror.  Fallback is minimal — a designed error page would
// replace it in real prod.
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <Sentry.ErrorBoundary
      fallback={({ error, resetError }) => (
        <div style={{ padding: 24, fontFamily: 'system-ui' }}>
          <h2>Bir şeyler ters gitti.</h2>
          <p style={{ color: '#666' }}>{String(error?.message || error)}</p>
          <button onClick={resetError} style={{ padding: '8px 16px', cursor: 'pointer' }}>
            Tekrar dene
          </button>
        </div>
      )}
    >
      <BrowserRouter>
        <AuthProvider>
          <WishlistProvider>
            <CartProvider>
              <ChatbotProvider>
                <App />
                <Toaster position="top-right" toastOptions={{ duration: 4000 }} />
              </ChatbotProvider>
            </CartProvider>
          </WishlistProvider>
        </AuthProvider>
      </BrowserRouter>
    </Sentry.ErrorBoundary>
  </React.StrictMode>,
);
