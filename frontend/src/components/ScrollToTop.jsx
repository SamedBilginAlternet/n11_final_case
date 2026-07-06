import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Resets the window scroll position to the top on every route change.
 * React Router v6 keeps the previous page's scroll offset by default, so
 * navigating (e.g. clicking a product from a scrolled-down listing) would
 * otherwise open the new page mid-scroll. Rendered once inside the router.
 */
export default function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
  }, [pathname]);

  return null;
}
