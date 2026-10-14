/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        coke: {
          red: '#f40009',
          dark: '#090b0d',
          soft: '#f5f5f6',
          warm: '#fff1f2',
          green: '#179438',
          orange: '#f29e14',
        },
      },
      boxShadow: {
        soft: '0 10px 30px rgba(15, 23, 42, 0.08)',
      },
    },
  },
  plugins: [],
};
