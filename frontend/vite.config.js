import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const coreTarget = env.VITE_FINBOUND_DEV_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    test: {
      environment: 'jsdom',
    },
    server: {
      port: 5173,
      proxy: {
        '/core-api': {
          target: coreTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/core-api/, ''),
        },
      },
    },
  }
})
