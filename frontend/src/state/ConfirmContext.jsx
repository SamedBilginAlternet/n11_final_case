import { createContext, useCallback, useContext, useRef, useState } from 'react';
import ConfirmDialog from '../components/ConfirmDialog.jsx';

/**
 * Provider that exposes a single `confirm({...})` function returning a
 * Promise<boolean>.  Mirrors the ergonomics of `window.confirm()` so call
 * sites can just `await confirm(...)` and get a yes/no, but the rendering
 * is a styled in-app modal instead of the browser's blocking native dialog.
 *
 * Why a Promise + ref pattern?  The dialog is one host instance mounted
 * once at the app root — the resolver of the in-flight promise is stashed
 * in a ref so onConfirm/onCancel know which awaiter to settle.  A new
 * confirm() call while one is open replaces the resolver, but the UI
 * already keeps things serial since we close before re-opening.
 *
 * Single responsibility: own the open/closed state and the awaiter
 * resolver — nothing about presentation, which lives in ConfirmDialog.
 */

const ConfirmContext = createContext(null);

export function ConfirmProvider({ children }) {
  const [open, setOpen] = useState(false);
  const [options, setOptions] = useState(null);
  const resolverRef = useRef(null);

  const confirm = useCallback((opts) => {
    setOptions(opts || {});
    setOpen(true);
    return new Promise((resolve) => {
      resolverRef.current = resolve;
    });
  }, []);

  const settle = useCallback((value) => {
    resolverRef.current?.(value);
    resolverRef.current = null;
    setOpen(false);
  }, []);

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <ConfirmDialog
        open={open}
        options={options}
        onConfirm={() => settle(true)}
        onCancel={() => settle(false)}
      />
    </ConfirmContext.Provider>
  );
}

export function useConfirm() {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error('useConfirm must be used inside <ConfirmProvider>');
  return ctx;
}
