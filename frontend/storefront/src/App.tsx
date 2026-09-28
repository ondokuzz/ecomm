import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router'
import { ApiError } from './api/http'
import { RequireAuth, StorefrontAuthProvider } from './auth/auth'
import { Layout } from './components/Layout'
import { EmptyState } from './components/Status'
import { ButtonLink } from './components/ui/Button'
import { CartPage } from './pages/CartPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { OrderPage } from './pages/OrderPage'
import { OrdersPage } from './pages/OrdersPage'
import { ProductDetailPage } from './pages/ProductDetailPage'
import { ProductListPage } from './pages/ProductListPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A 4xx won't change on retry; a network blip or 5xx might.
      retry: (failures, error) => failures < 2 && !(error instanceof ApiError && error.status < 500),
    },
  },
})

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <StorefrontAuthProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<ProductListPage />} />
              <Route path="products/:sku" element={<ProductDetailPage />} />
              <Route path="cart" element={<RequireAuth><CartPage /></RequireAuth>} />
              <Route path="checkout" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
              <Route path="orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
              <Route path="orders/:id" element={<RequireAuth><OrderPage /></RequireAuth>} />
              <Route path="*" element={<NotFound />} />
            </Route>
          </Routes>
        </StorefrontAuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

function NotFound() {
  return (
    <EmptyState
      title="Page not found"
      action={
        <ButtonLink variant="primary" to="/">
          Back to products
        </ButtonLink>
      }
    >
      There is nothing at this address.
    </EmptyState>
  )
}
