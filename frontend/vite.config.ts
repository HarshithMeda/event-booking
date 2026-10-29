import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // During development (npm run dev on :5173), send API calls to the Spring Boot backend.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
