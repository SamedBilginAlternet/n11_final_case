import HeroSection from '../components/hero/HeroSection.jsx';
import HomeSection from '../components/home/HomeSection.jsx';
import CampaignBlock from '../components/campaign/CampaignBlock.jsx';
import { campaigns } from '../data/campaigns.js';

/**
 * Long-scroll homepage — hero on top, then category-shelf rails interspersed
 * with the existing campaign blocks. Order intentionally puts the highest-
 * margin / fastest-moving categories (tech, fashion) above the fold and
 * leaves long-tail (auto/garden) deeper, mirroring real marketplace layouts.
 */
export default function HomePage() {
  return (
    <div className="space-y-6 md:space-y-8">
      <HeroSection />

      <HomeSection
        title="Yenilikler"
        subtitle="Yeni eklenen ürünler"
        emoji="✨"
        sort="newest"
        size={6}
        viewAllHref="/catalog?sort=newest"
        accent="pink"
      />

      <HomeSection
        title="Teknoloji"
        subtitle="Telefon, bilgisayar, ses sistemleri"
        emoji="📱"
        categorySlug="elektronik"
        size={6}
        viewAllHref="/catalog?category=elektronik"
      />

      {/* Existing pink "Ev Yenilik Günleri" + ev-yasam image campaign */}
      {campaigns.map((c) => (
        <CampaignBlock key={c.id} campaign={c} />
      ))}

      <HomeSection
        title="Spor & Outdoor"
        subtitle="Egzersiz, koşu, yoga"
        emoji="🏃"
        categorySlug="spor"
        size={6}
        viewAllHref="/catalog?category=spor"
      />

      <HomeSection
        title="Anne & Bebek"
        subtitle="Bebek bakımı, oyuncak, anne sağlığı"
        emoji="👶"
        categorySlug="anne-bebek"
        size={6}
        viewAllHref="/catalog?category=anne-bebek"
      />

      <HomeSection
        title="Moda"
        subtitle="Giyim, çanta, aksesuar"
        emoji="👗"
        categorySlug="moda"
        size={6}
        viewAllHref="/catalog?category=moda"
      />

      <HomeSection
        title="Kozmetik"
        subtitle="Cilt bakımı, makyaj, parfüm"
        emoji="💄"
        categorySlug="kozmetik"
        size={6}
        viewAllHref="/catalog?category=kozmetik"
      />

      <HomeSection
        title="Çok Satanlar"
        subtitle="En çok değerlendirme alan ürünler"
        emoji="🔥"
        sort="rating"
        size={6}
        viewAllHref="/catalog?sort=rating"
        accent="dark"
      />

      <HomeSection
        title="Kitap & Müzik"
        subtitle="Kurgu, kişisel gelişim, klasikler"
        emoji="📚"
        categorySlug="kitap"
        size={6}
        viewAllHref="/catalog?category=kitap"
      />

      <HomeSection
        title="Oto & Bahçe"
        subtitle="Araç aksesuarları, bahçe ekipmanları"
        emoji="🌿"
        categorySlug="oto-bahce"
        size={6}
        viewAllHref="/catalog?category=oto-bahce"
      />
    </div>
  );
}
