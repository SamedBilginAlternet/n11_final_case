import SeoText from './footer/SeoText.jsx';
import FeatureStrip from './footer/FeatureStrip.jsx';
import FooterLinkColumn from './footer/FooterLinkColumn.jsx';
import StoresCard from './footer/StoresCard.jsx';
import AppBadges from './footer/AppBadges.jsx';
import SocialIcons from './footer/SocialIcons.jsx';
import SecurityLogos from './footer/SecurityLogos.jsx';
import { footerColumns } from '../data/footer.js';

export default function Footer() {
  return (
    <footer className="mt-12 space-y-6 bg-gray-50 pb-8">
      <div className="mx-auto max-w-7xl space-y-6 px-4">
        <SeoText />
        <FeatureStrip />

        <div className="grid grid-cols-2 gap-6 lg:grid-cols-5">
          {footerColumns.map((col) => (
            <FooterLinkColumn key={col.id} column={col} />
          ))}
          <StoresCard />
        </div>

        <div className="border-t border-gray-200 pt-4">
          <div className="flex flex-wrap items-center justify-between gap-6">
            <AppBadges />
            <SocialIcons />
            <SecurityLogos />
          </div>
        </div>

        <p className="pt-2 text-center text-xs text-gray-400">
          © {new Date().getFullYear()} n11 — TalentHub Bootcamp final case
        </p>
      </div>
    </footer>
  );
}
