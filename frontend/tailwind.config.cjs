/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#f0f5ec',
          100: '#e1ead9',
          200: '#c8dab9',
          300: '#a8c394',
          400: '#84a772',
          500: '#628752',
          600: '#42683a',
          700: '#34532e',
          800: '#294328',
          900: '#203523'
        }
      },
      fontFamily: {
        sans: ['DM Sans', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        card: '0 12px 36px rgba(32, 53, 35, 0.055)'
      },
      borderRadius: {
        xl2: '1rem'
      }
    }
  },
  plugins: []
};
