import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Star, Trash2 } from 'lucide-react';
import { motion } from 'framer-motion';
import { api } from '../../api/client.js';
import { useAuth } from '../../state/AuthContext.jsx';
import { useConfirm } from '../../state/ConfirmContext.jsx';
import RatingStars from './RatingStars.jsx';

export default function ReviewsSection({ productId, onAggregateChange }) {
  const { isAuthed } = useAuth();
  const confirm = useConfirm();
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [mine, setMine] = useState(null);
  const [draft, setDraft] = useState({ rating: 5, body: '' });
  const [saving, setSaving] = useState(false);

  async function loadAll() {
    setLoading(true);
    try {
      const [{ data: page }, mineRes] = await Promise.all([
        api.get(`/api/products/${productId}/reviews`),
        isAuthed
          ? api.get(`/api/products/${productId}/reviews/mine`).catch(() => ({ data: null }))
          : Promise.resolve({ data: null }),
      ]);
      setReviews(page.content || []);
      const myReview = mineRes?.data || null;
      setMine(myReview);
      if (myReview) setDraft({ rating: myReview.rating, body: myReview.body || '' });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productId, isAuthed]);

  async function onSubmit(e) {
    e.preventDefault();
    setSaving(true);
    try {
      await api.put(`/api/products/${productId}/reviews`, draft);
      toast.success(mine ? 'Yorumun güncellendi' : 'Yorumun yayınlandı');
      await loadAll();
      onAggregateChange?.();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Yorum gönderilemedi');
    } finally {
      setSaving(false);
    }
  }

  async function onDelete() {
    const ok = await confirm({
      title: 'Yorumu sil',
      message: 'Yorumunu silmek istediğine emin misin? Bu işlem geri alınamaz.',
      confirmLabel: 'Sil',
      cancelLabel: 'Vazgeç',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      await api.delete(`/api/products/${productId}/reviews`);
      toast('Yorumun silindi');
      setMine(null);
      setDraft({ rating: 5, body: '' });
      await loadAll();
      onAggregateChange?.();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Silinemedi');
    }
  }

  return (
    <section className="space-y-4">
      <header className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Değerlendirmeler ({reviews.length})</h2>
      </header>

      {!isAuthed ? (
        <div className="card p-3 text-sm text-gray-600">
          <Link to="/login" className="font-medium text-n11-pink hover:underline">Giriş yap</Link> ya da{' '}
          <Link to="/register" className="font-medium text-n11-pink hover:underline">üye ol</Link>{' '}
          — bu ürünü değerlendirebilirsin.
        </div>
      ) : (
        <form onSubmit={onSubmit} className="card space-y-3 p-4">
          <p className="text-sm font-medium">{mine ? 'Yorumunu güncelle' : 'Bu ürünü değerlendir'}</p>
          <StarPicker value={draft.rating} onChange={(v) => setDraft({ ...draft, rating: v })} />
          <textarea
            value={draft.body}
            onChange={(e) => setDraft({ ...draft, body: e.target.value })}
            placeholder="Deneyimini birkaç cümleyle paylaş (opsiyonel)"
            maxLength={2000}
            rows={3}
            className="input w-full resize-none"
          />
          <div className="flex justify-end gap-2">
            {mine && (
              <button type="button" onClick={onDelete} className="btn-outline flex items-center gap-1.5">
                <Trash2 className="h-4 w-4" /> Sil
              </button>
            )}
            <button type="submit" disabled={saving} className="btn-primary">
              {saving ? '…' : mine ? 'Güncelle' : 'Yayınla'}
            </button>
          </div>
        </form>
      )}

      {loading && <div className="card h-20 animate-pulse bg-gray-100" />}

      {!loading && reviews.length === 0 && (
        <p className="text-sm text-gray-500">Henüz yorum yok — ilk değerlendirme senin olsun.</p>
      )}

      <ul className="space-y-3">
        {reviews.map((r) => (
          <ReviewItem key={r.id} review={r} isMine={mine?.id === r.id} />
        ))}
      </ul>
    </section>
  );
}

function ReviewItem({ review, isMine }) {
  return (
    <motion.li
      layout
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      className="card p-4"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-medium text-gray-800">
            {review.userName} {isMine && <span className="ml-1 text-xs text-n11-pink">(senin)</span>}
          </p>
          <RatingStars value={review.rating} />
        </div>
        <time className="text-xs text-gray-400">
          {new Date(review.createdAt).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' })}
        </time>
      </div>
      {review.body && <p className="mt-2 whitespace-pre-line text-sm text-gray-700">{review.body}</p>}
    </motion.li>
  );
}

function StarPicker({ value, onChange }) {
  const [hover, setHover] = useState(0);
  const display = hover || value;
  return (
    <div className="flex items-center gap-1" onMouseLeave={() => setHover(0)}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          onClick={() => onChange(n)}
          onMouseEnter={() => setHover(n)}
          aria-label={`${n} yıldız`}
          className="transition"
        >
          <Star
            className={`h-7 w-7 ${n <= display ? 'text-amber-400' : 'text-gray-300'}`}
            fill={n <= display ? 'currentColor' : 'none'}
            strokeWidth={1.5}
          />
        </button>
      ))}
      <span className="ml-2 text-sm text-gray-600">{display}/5</span>
    </div>
  );
}
