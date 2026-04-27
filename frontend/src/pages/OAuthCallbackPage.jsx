import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../state/AuthContext.jsx';

export default function OAuthCallbackPage() {
  const { hydrateFromToken } = useAuth();
  const navigate = useNavigate();
  const ranRef = useRef(false);

  useEffect(() => {
    if (ranRef.current) return;
    ranRef.current = true;

    const fragment = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : '';
    const params = new URLSearchParams(fragment);
    const token = params.get('token');

    if (!token) {
      navigate('/login?oauth_error=missing_token', { replace: true });
      return;
    }

    hydrateFromToken(token)
      .then(() => navigate('/', { replace: true }))
      .catch(() => navigate('/login?oauth_error=hydration_failed', { replace: true }));
  }, [hydrateFromToken, navigate]);

  return (
    <div className="mx-auto mt-12 max-w-md p-6 text-center">
      <p className="text-sm text-gray-500">Giriş tamamlanıyor…</p>
    </div>
  );
}
