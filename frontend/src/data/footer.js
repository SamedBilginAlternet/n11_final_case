export const footerColumns = [
  {
    id: 'corporate',
    title: 'Kurumsal',
    links: [
      { label: 'Hakkımızda',     to: '/about' },
      { label: 'İletişim',       to: '/contact' },
      { label: 'Kariyer',        to: '/careers' },
      { label: 'KVKK',           to: '/kvkk' },
      { label: 'Sürdürülebilirlik', to: '/sustainability' },
    ],
  },
  {
    id: 'help',
    title: 'Yardım',
    links: [
      { label: 'Sıkça Sorulan Sorular', to: '/faq' },
      { label: 'Sipariş Takibi',         to: '/orders' },
      { label: 'İade ve Değişim',        to: '/returns' },
      { label: 'Kargo Takibi',           to: '/shipping' },
      { label: 'Mesafeli Satış',         to: '/legal/sales' },
    ],
  },
  {
    id: 'shopping',
    title: 'Alışveriş',
    links: [
      { label: 'Tüm Ürünler',     to: '/catalog' },
      { label: 'Kuponlarım',      to: '/account/coupons' },
      { label: 'Hediye Kartları', to: '/gift-cards' },
      { label: 'Kampanyalar',     to: '/campaigns' },
      { label: 'Süper Fırsatlar', to: '/super-deals' },
    ],
  },
  {
    id: 'account',
    title: 'Hesabım',
    links: [
      { label: 'Profilim',        to: '/account' },
      { label: 'Adreslerim',      to: '/account/addresses' },
      { label: 'Kayıtlı Kartlar', to: '/account/cards' },
      { label: 'Bildirim Tercihleri', to: '/account/notifications' },
      { label: 'Çıkış Yap',       to: '/logout' },
    ],
  },
];

export const seoText = {
  title: 'Online Alışverişin Adresi n11',
  body: `**n11**, milyonlarca ürünü güvenli alışveriş deneyimiyle bir araya getiren Türkiye'nin önde gelen
**e-ticaret** platformudur. **Moda**, **elektronik**, **ev & yaşam**, **kozmetik**, **spor** ve daha pek
çok kategoride binlerce **marka** ve **ürün** seni bekliyor. **Kapıda ödeme**, **hızlı kargo**,
**kolay iade** ve **kupon avantajları** ile **online alışveriş** keyfini her zamankinden
ayrıcalıklı yaşamak için n11.com'a hemen göz at. **Aklındaki her şey n11'de.**`,
};

export const storesCard = {
  title: 'Mağazalar',
  description: 'Sen de n11 mağazasını aç, milyonlara ulaş.',
  primary: { label: 'Mağaza Girişi',     to: '/seller/login', variant: 'outline' },
  secondary: { label: 'Ücretsiz Mağaza Aç', to: '/seller/register', variant: 'dark' },
  links: [
    { label: 'Satıcı Yardımı',     to: '/seller/help' },
    { label: 'Komisyon Oranları',  to: '/seller/commissions' },
  ],
};

export const socialMedia = [
  { id: 'instagram', label: 'Instagram', icon: 'IG' },
  { id: 'facebook',  label: 'Facebook',  icon: 'FB' },
  { id: 'twitter',   label: 'X',         icon: '𝕏' },
  { id: 'youtube',   label: 'YouTube',   icon: 'YT' },
];

export const securityLogos = [
  { id: 'etbis', label: 'ETBİS', subtitle: 'Doğrulanmış Hizmet Sağlayıcı' },
  { id: 'trgo',  label: 'TR.GO', subtitle: 'Güvenli Alışveriş' },
];

export const appBadges = [
  { id: 'app-store',   label: 'App Store',   sub: 'İndir' },
  { id: 'google-play', label: 'Google Play', sub: 'İndir' },
];
