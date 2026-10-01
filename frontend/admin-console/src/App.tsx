import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { ApiError } from './api/http'
import { AdminConsoleAuthProvider, RequireStaff } from './auth/auth'
import { Layout } from './components/Layout'
import { NotFound } from './components/NotFound'
import { CategoryEditorPage } from './pages/CategoryEditorPage'
import { CategoryListPage } from './pages/CategoryListPage'

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
                <Route index element={<Navigate to="/categories" replace />} />
                <Route path="categories" element={<CategoryListPage />} />
                <Route path="categories/new" element={<CategoryEditorPage />} />
                <Route path="categories/:slug" element={<CategoryEditorPage />} />
                <Route path="*" element={<NotFound />} />
              </Route>
            </Routes>
          </RequireStaff>
        </AdminConsoleAuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
