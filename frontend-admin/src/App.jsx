import { Navigate, Route, Routes } from 'react-router-dom';
import AdminGuard from './components/AdminGuard.jsx';
import AdminLayout from './components/AdminLayout.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import CategoriesPage from './pages/CategoriesPage.jsx';
import CouponsPage from './pages/CouponsPage.jsx';
import OrdersPage from './pages/OrdersPage.jsx';
import ProductsPage from './pages/ProductsPage.jsx';
import UsersPage from './pages/UsersPage.jsx';

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
        <Route path="/orders" element={<OrdersPage />} />
        <Route path="/products" element={<ProductsPage />} />
        <Route path="/categories" element={<CategoriesPage />} />
        <Route path="/coupons" element={<CouponsPage />} />
        <Route path="/users" element={<UsersPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
