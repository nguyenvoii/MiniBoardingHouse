import { defineConfig } from 'vite';

const backend = { target: 'http://localhost:8080', changeOrigin: true };

// Dev: `npm run dev` (cổng 5173) chuyển /api sang Spring Boot cổng 8080 nên không cần CORS.
// Build: `npm run build` ghi vào src/main/resources/static để Spring Boot phục vụ chung cổng 8080.
export default defineConfig({
  base: './',
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      '^/(privacy|terms|pricing|contact)$': backend,
      '^/assets/stayly-(legal\\.(css|js)$|mark\\.svg$|font/)': backend,
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    target: 'es2022',
  },
});
