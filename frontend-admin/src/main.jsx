import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import App from './App.jsx';
import { AuthProvider } from './state/AuthContext.jsx';
import './styles/index.css';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {/*
      basename matches Vite's `base: '/admin/'` so React Router treats
      the panel as if it owns the root.  Routes in the app stay '/' or
      '/orders' etc; Router transparently prepends '/admin'.  Without
      this, every navigation would push '/admin/admin/...' or 404.
    */}
    <BrowserRouter basename="/admin">
      <AuthProvider>
        <App />
        <Toaster position="top-right" toastOptions={{ duration: 4000 }} />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
