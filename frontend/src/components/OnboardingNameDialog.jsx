import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import toast from 'react-hot-toast';
import { api, performRefresh } from '../api/client.js';
import { useAuth } from '../state/AuthContext.jsx';

const SKIP_KEY = 'n11.onboarding.nameSkipped';

/**
 * One-shot dialog that asks phone-only signups for their name on first
 * load.  Kept out of the auth flow itself so that login stays fast and
 * the user can browse before deciding to personalise.
 *
 * Skipping persists in localStorage so the prompt doesn't nag on every
 * page load — the profile page still exposes the same edit form for
 * later changes.
 */
export default function OnboardingNameDialog() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!user) { setOpen(false); return; }
    if (user.fullName && user.fullName.trim()) { setOpen(false); return; }
    if (typeof window !== 'undefined' && window.localStorage?.getItem(SKIP_KEY)) {
      setOpen(false);
      return;
    }
    // Nudge — but only once we've established who's looking at the page.
    setOpen(true);
  }, [user]);

  async function save(e) {
    e.preventDefault();
    if (!name.trim()) return;
    setBusy(true);
    try {
      await api.patch('/api/users/me', { fullName: name.trim() });
      // Refresh JWT so downstream services (order-service, notification)
      // see the populated fullName claim immediately.
      await performRefresh().catch(() => {});
      toast.success('Hoş geldin, ' + name.trim().split(' ')[0]);
      setOpen(false);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Kaydedilemedi');
    } finally {
      setBusy(false);
    }
  }

  function skip() {
    try { window.localStorage?.setItem(SKIP_KEY, '1'); } catch { /* private mode */ }
    setOpen(false);
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18 }}
        >
          <motion.div
            className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
            initial={{ y: 24, scale: 0.98 }}
            animate={{ y: 0, scale: 1 }}
            exit={{ y: 24, scale: 0.98 }}
            transition={{ duration: 0.22, ease: 'easeOut' }}
          >
            <h2 className="text-lg font-semibold text-gray-800">Sana nasıl seslenelim?</h2>
            <p className="mt-1 text-sm text-gray-500">
              Profilini kişiselleştirmek için adın yeterli.
            </p>
            <form onSubmit={save} className="mt-5 space-y-3">
              <input
                type="text"
                autoFocus
                placeholder="Ad Soyad"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="input w-full"
                autoComplete="name"
              />
              <div className="flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={skip}
                  disabled={busy}
                  className="text-sm text-gray-500 hover:text-gray-700"
                >
                  Şimdilik atla
                </button>
                <button
                  type="submit"
                  disabled={busy || !name.trim()}
                  className="btn-primary text-sm"
                >
                  {busy ? 'Kaydediliyor…' : 'Kaydet'}
                </button>
              </div>
            </form>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
