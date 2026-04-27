import HeroSection from '../components/hero/HeroSection.jsx';
import CampaignBlock from '../components/campaign/CampaignBlock.jsx';
import { campaigns } from '../data/campaigns.js';

export default function HomePage() {
  return (
    <div className="space-y-6">
      <HeroSection />
      {campaigns.map((c) => (
        <CampaignBlock key={c.id} campaign={c} />
      ))}
    </div>
  );
}
