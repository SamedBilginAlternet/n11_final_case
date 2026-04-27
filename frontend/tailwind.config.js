/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        n11: {
          pink: '#F5167E',
          pinkDark: '#C1035E',
          pinkSoft: '#FDE3EE',
          pinkBg: '#FFF4F9',
          black: '#111111',
          gray: '#444444',
          line: '#E5E7EB',
        },
      },
      fontFamily: {
        sans: ['Inter', 'Roboto', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      backgroundImage: {
        'pink-gradient': 'linear-gradient(90deg, #FCE7F3 0%, #F5167E 100%)',
      },
      boxShadow: {
        soft: '0 1px 2px rgba(0,0,0,0.04), 0 1px 12px rgba(0,0,0,0.04)',
      },
      lineClamp: { 2: '2' },
    },
  },
  plugins: [],
};
