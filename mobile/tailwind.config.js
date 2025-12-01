/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./App.{js,jsx,ts,tsx}', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#4FD1C5',
        background: '#F7FAFC',
        muted: '#A0AEC0',
        card: '#FFFFFF'
      }
    }
  },
  plugins: []
};
