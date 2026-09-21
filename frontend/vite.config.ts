import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置：路径别名 + 开发代理（/api -> 后端 8080）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      // 前后端分离联调代理：SSE 流式接口同样走该代理
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
