import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../state/AuthContext.jsx';

/**
 * Route guard for the admin panel.
 *
 * <p>The backend's @PreAuthorize("hasRole('ADMIN')") is the real authority
 * here; this guard exists only for UX, so non-admin users see a clear
 * redirect to /login instead of a stream of 403 toasts.</p>
 */
export default function AdminGuard({ children }) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (user.role !== 'ADMIN') {
    // This shouldn't happen because login already filters non-admins, but
    // keep the check so a stale localStorage entry can't slip through.
    return <Navigate to="/login" replace />;
  }
  return children;
}
