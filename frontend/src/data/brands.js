// Each brand maps to its primary public domain. The BrandGrid component
// fetches the logo via clearbit.com/logo/<domain> at runtime; if that
// 404s it falls back to Google's favicon proxy, then to the initials.
// No assets are bundled — keeps the build clean and lets the favicons
// stay current with whatever the brand publishes.
export const homeBrands = [
  { id: 'english-home', name: 'English Home', domain: 'englishhome.com.tr', initials: 'EH' },
  { id: 'yatas',        name: 'Yataş',        domain: 'yatas.com.tr',      initials: 'YT' },
  { id: 'karaca',       name: 'Karaca',       domain: 'karaca.com',        initials: 'KR' },
  { id: 'madame-coco',  name: 'Madame Coco',  domain: 'madamecoco.com',    initials: 'MC' },
  { id: 'tac',          name: 'Taç',          domain: 'tacstore.com',      initials: 'TC' },
  { id: 'ozdilek',      name: 'Özdilek',      domain: 'ozdilekteyim.com',  initials: 'OZ' },
];
