import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: true,
    port: 5173,
    // 本地联调：把 /api 代理到 Spring Boot 服务
    proxy: {
      '/api': {
        target: process.env.CAPTCHA_API_TARGET || 'http://127.0.0.1:8088',
        changeOrigin: true,
      },
    },
  },
})
