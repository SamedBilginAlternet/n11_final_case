import { Navigate, Route, Routes } from 'react-router-dom';
import Header from './components/Header.jsx';
import Footer from './components/Footer.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import ChatBubbleButton from './components/chatbot/ChatBubbleButton.jsx';
import ChatPanel from './components/chatbot/ChatPanel.jsx';
import HomePage from './pages/HomePage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import OAuthCallbackPage from './pages/OAuthCallbackPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import ProductListPage from './pages/ProductListPage.jsx';
import ProductDetailPage from './pages/ProductDetailPage.jsx';
import CartPage from './pages/CartPage.jsx';
import OrdersPage from './pages/OrdersPage.jsx';
import AddressBookPage from './pages/AddressBookPage.jsx';

export default function App() {
  return (
    <div className="flex min-h-full flex-col bg-gray-50">
      <Header />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/catalog" element={<ProductListPage />} />
          <Route path="/products/:slug" element={<ProductDetailPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/auth/callback" element={<OAuthCallbackPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/cart"
            element={
              <ProtectedRoute>
                <CartPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/orders"
            element={
              <ProtectedRoute>
                <OrdersPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/account/addresses"
            element={
              <ProtectedRoute>
                <AddressBookPage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <Footer />
      <ChatBubbleButton />
      <ChatPanel />
    </div>
  );
}
