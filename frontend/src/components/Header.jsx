import TopBar from './header/TopBar.jsx';
import CategoryNav from './header/CategoryNav.jsx';

export default function Header() {
  return (
    <header className="sticky top-0 z-30 bg-white">
      <TopBar />
      <CategoryNav />
    </header>
  );
}
