import Logo from './Logo.jsx';
import SearchBar from './SearchBar.jsx';
import UserActions from './UserActions.jsx';

export default function TopBar() {
  return (
    <div className="border-b border-gray-100 bg-white">
      {/*
        Mobile (<md): two-row layout — logo + actions on top, full-width
        search drops below via order-last + w-full. Desktop (>=md) collapses
        back into a single row with the search expanding (flex-1).
      */}
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-3 px-3 py-2 md:flex-nowrap md:gap-6 md:px-4 md:py-3">
        <Logo />
        <div className="ml-auto md:ml-0 md:order-last">
          <UserActions />
        </div>
        <div className="order-last w-full md:order-none md:w-auto md:flex-1">
          <SearchBar />
        </div>
      </div>
    </div>
  );
}
