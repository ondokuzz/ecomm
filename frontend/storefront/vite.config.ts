import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

/**
 * In development each service is reached at `/api/<service>` through this proxy, on the host port
 * compose publishes it on, the same paths nginx serves in compose. The services send no CORS
 * headers, so the browser must reach them on the Storefront's own origin.
 */
const services = {
  catalog: 8081,
  inventory: 8082,
  cart: 8083,
  'checkout-pricing': 8086,
  'order-management': 8085,
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: Object.fromEntries(
      Object.entries(services).map(([service, port]) => [
        `/api/${service}`,
        {
          target: `http://localhost:${port}`,
          rewrite: (path: string) => path.slice(`/api/${service}`.length),
        },
      ]),
    ),
  },
  test: {
    environment: 'node',
  },
})
