/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        navy: {
          DEFAULT: '#10233F',
          dark: '#0A172A',
          light: '#1B355E'
        },
        emergency: {
          DEFAULT: '#DC2626',
          dark: '#B91C1C',
          light: '#EF4444'
        },
        teal: {
          DEFAULT: '#18B6A4',
          dark: '#118B7D',
          light: '#2FE1CD'
        },
        canvas: '#F4F7FB',
        ink: '#172033'
      }
    },
  },
  plugins: [],
}
