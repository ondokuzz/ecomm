import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { ApiError } from './api/http'
import { AdminConsoleAuthProvider, RequireStaff } from './auth/auth'
import { Layout } from './components/Layout'
import { NotFound } from './components/NotFound'
import { CampaignEditorPage } from './pages/CampaignEditorPage'
import { CampaignListPage } from './pages/CampaignListPage'
import { CategoryEditorPage } from './pages/CategoryEditorPage'
import { CategoryListPage } from './pages/CategoryListPage'
import { CouponEditorPage } from './pages/CouponEditorPage'
import { CouponListPage } from './pages/CouponListPage'
import { ProductEditorPage } from './pages/ProductEditorPage'
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
        <AdminConsoleAuthProvider>
          <RequireStaff>
            <Routes>
              <Route element={<Layout />}>
                <Route index element={<Navigate to="/products" replace />} />
                <Route path="products" element={<ProductListPage />} />
                <Route path="products/new" element={<ProductEditorPage />} />
                <Route path="products/:sku" element={<ProductEditorPage />} />
                <Route path="categories" element={<CategoryListPage />} />
                <Route path="categories/new" element={<CategoryEditorPage />} />
                <Route path="categories/:slug" element={<CategoryEditorPage />} />
                <Route path="promotions" element={<Navigate to="/promotions/coupons" replace />} />
                <Route path="promotions/coupons" element={<CouponListPage />} />
                <Route path="promotions/coupons/new" element={<CouponEditorPage />} />
                <Route path="promotions/coupons/:code" element={<CouponEditorPage />} />
                <Route path="promotions/campaigns" element={<CampaignListPage />} />
                <Route path="promotions/campaigns/new" element={<CampaignEditorPage />} />
                <Route path="promotions/campaigns/:id" element={<CampaignEditorPage />} />
                <Route path="*" element={<NotFound />} />
              </Route>
            </Routes>
          </RequireStaff>
        </AdminConsoleAuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
