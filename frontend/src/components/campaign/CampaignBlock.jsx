import BrandGrid from './BrandGrid.jsx';
import ProductRail from '../product/ProductRail.jsx';

export default function CampaignBlock({ campaign }) {
  const isPink = campaign.background === 'pink';
  const wrapperClass = isPink
    ? 'rounded-2xl bg-n11-pink p-5 text-white'
    : 'rounded-2xl bg-white p-5 ring-1 ring-gray-200';

  return (
    <section className={wrapperClass}>
      <div className="grid gap-5 lg:grid-cols-[3fr_7fr]">
        <aside className="flex flex-col">
          <h2 className={`text-2xl font-extrabold leading-tight ${isPink ? 'text-white' : 'text-n11-black'}`}>
            {campaign.title}
          </h2>
          <p className={`mt-1 text-sm ${isPink ? 'text-white/90' : 'text-gray-500'}`}>{campaign.subtitle}</p>

          <div className="mt-4 flex-1">
            {campaign.layout === 'brand-grid' ? (
              <BrandGrid />
            ) : (
              <div
                className="aspect-[4/5] w-full rounded-lg bg-gray-100 bg-cover bg-center"
                style={campaign.bgImage ? { backgroundImage: `url(${campaign.bgImage})` } : {}}
              />
            )}
          </div>
        </aside>

        <div className={isPink ? 'rounded-xl bg-white p-3' : ''}>
          <ProductRail categorySlug={campaign.productCategorySlug} size={6} />
        </div>
      </div>
    </section>
  );
}
