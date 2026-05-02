import { useEffect, useRef } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { AlertTriangle } from 'lucide-react';

/**
 * Pure presentational confirm dialog — knows how it looks, nothing about who
 * opened it or what happens when the user clicks.  All state is owned by
 * ConfirmContext; this component just renders props and forwards click
 * intent.  Single responsibility: dialog presentation + accessibility (focus
 * trap on the confirm button, Escape closes).
 */
export default function ConfirmDialog({ open, options, onConfirm, onCancel }) {
  const confirmBtn = useRef(null);

  useEffect(() => {
    if (!open) return;
    confirmBtn.current?.focus();
    function onKey(e) {
      if (e.key === 'Escape') onCancel();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onCancel]);

  const {
    title = 'Emin misin?',
    message = '',
    confirmLabel = 'Onayla',
    cancelLabel = 'Vazgeç',
    tone = 'danger',
  } = options || {};

  const confirmClass = tone === 'danger'
    ? 'inline-flex items-center justify-center rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-400'
    : 'btn-primary';

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.15 }}
          onClick={onCancel}
          role="presentation"
        >
          <motion.div
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="confirm-title"
            aria-describedby="confirm-body"
            className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-xl"
            initial={{ y: 16, scale: 0.98 }}
            animate={{ y: 0, scale: 1 }}
            exit={{ y: 16, scale: 0.98 }}
            transition={{ duration: 0.18, ease: 'easeOut' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start gap-3">
              {tone === 'danger' && (
                <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-red-50 text-red-500">
                  <AlertTriangle className="h-5 w-5" />
                </span>
              )}
              <div className="flex-1">
                <h2 id="confirm-title" className="text-base font-semibold text-gray-800">{title}</h2>
                {message && (
                  <p id="confirm-body" className="mt-1 text-sm text-gray-500">{message}</p>
                )}
              </div>
            </div>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={onCancel}
                className="rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                {cancelLabel}
              </button>
              <button
                ref={confirmBtn}
                type="button"
                onClick={onConfirm}
                className={confirmClass}
              >
                {confirmLabel}
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
