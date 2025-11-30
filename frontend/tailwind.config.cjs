/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#f2fbfa',
          100: '#d8f3ef',
          200: '#b1e7df',
          300: '#7dd8c9',
          400: '#52c4b0',
          500: '#2a9c8b',
          600: '#1e7d71',
          700: '#19645c',
          800: '#184f4a',
          900: '#153f3d'
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        card: '0 8px 30px rgba(15, 23, 42, 0.08)'
      },
      borderRadius: {
        xl2: '1rem'
      }
    }
  },
  plugins: []
};
