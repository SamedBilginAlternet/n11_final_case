import Logo from './Logo.jsx';
import SearchBar from './SearchBar.jsx';
import UserActions from './UserActions.jsx';

export default function TopBar() {
  return (
    <div className="border-b border-gray-100 bg-white">
      <div className="mx-auto flex max-w-7xl items-center gap-6 px-4 py-3">
        <Logo />
        <SearchBar />
        <UserActions />
      </div>
    </div>
  );
}
