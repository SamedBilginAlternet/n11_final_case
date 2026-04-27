import NewsBar from './NewsBar.jsx';
import MainBanner from './MainBanner.jsx';
import QuickLinksPills from './QuickLinksPills.jsx';

export default function HeroSection() {
  return (
    <section className="space-y-4">
      <NewsBar />
      <MainBanner />
      <QuickLinksPills />
    </section>
  );
}
