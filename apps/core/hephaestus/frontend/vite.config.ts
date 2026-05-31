import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { TanStackRouterVite } from '@tanstack/router-plugin/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    TanStackRouterVite({
      routesDirectory: 'src/routes',
      generatedRouteTree: 'src/routeTree.gen.ts',
    }),
    react(),
  ],
  server: {
    proxy: {
      '/api/themis': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/themis/, ''),
      },
      '/api/palamedes': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/palamedes/, ''),
      },
      '/api/metrics-adapter': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/metrics-adapter/, ''),
      },
      '/api/graphdb': {
        target: 'http://localhost:7200',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/graphdb/, ''),
      },
      '/api/rabbitmq': {
        target: 'http://localhost:15672',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/rabbitmq/, ''),
      },
      '/api/metis': {
        target: 'http://localhost:50052',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/metis/, ''),
      },
      '/api/prometheus': {
        target: 'http://localhost:9090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/prometheus/, ''),
      },
      '/api': {
        target: 'http://localhost:8086',
        changeOrigin: true,
      }
    }
  }
})
