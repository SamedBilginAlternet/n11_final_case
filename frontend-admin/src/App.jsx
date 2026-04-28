import { Navigate, Route, Routes } from 'react-router-dom';
import AdminGuard from './components/AdminGuard.jsx';
import AdminLayout from './components/AdminLayout.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import PlaceholderPage from './pages/PlaceholderPage.jsx';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <AdminGuard>
            <AdminLayout />
          </AdminGuard>
        }
      >
        <Route path="/" element={<DashboardPage />} />
        <Route path="/orders" element={<PlaceholderPage title="Siparişler" description="Sipariş yaşam döngüsü yönetimi (yakında)." />} />
        <Route path="/products" element={<PlaceholderPage title="Ürünler" description="Ürün CRUD (yakında)." />} />
        <Route path="/coupons" element={<PlaceholderPage title="Kuponlar" description="Kupon ve kampanya yönetimi (yakında)." />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
