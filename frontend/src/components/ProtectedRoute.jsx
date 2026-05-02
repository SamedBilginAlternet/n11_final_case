import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../state/AuthContext.jsx';

export default function ProtectedRoute({ children }) {
  const { isAuthed, booting } = useAuth();
  const location = useLocation();

  // Don't redirect while the silent /refresh on first mount is still in
  // flight — otherwise external links (mail "Siparişlerim", OAuth callback)
  // bounce to /login even though the refresh cookie is valid.
  if (booting) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center text-sm text-neutral-500">
        Yükleniyor…
      </div>
    );
  }

  if (!isAuthed) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return children;
}
