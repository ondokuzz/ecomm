import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Category, CategoryRequest } from '../domain/category'
import { api } from './http'

const categoriesKey = ['categories'] as const

export function useCategories() {
  return useQuery({
    queryKey: categoriesKey,
    queryFn: () => api<Category[]>('catalog', '/categories'),
  })
}

export function useCategory(slug: string | undefined) {
  return useQuery({
    queryKey: [...categoriesKey, slug],
    queryFn: () => api<Category>('catalog', `/categories/${encodeURIComponent(slug!)}`),
    enabled: slug !== undefined,
  })
}

/** Creates a Category, or replaces the one named `slug` with the request's name and definitions. */
export function useSaveCategory(slug: string | undefined) {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CategoryRequest) =>
      slug === undefined
        ? api<Category>('catalog', '/categories', { method: 'POST', body: request, token })
        : api<Category>('catalog', `/categories/${encodeURIComponent(slug)}`, {
            method: 'PUT',
            body: { name: request.name, attributes: request.attributes },
            token,
          }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: categoriesKey }),
  })
}

/** Deletes a Category; Catalog refuses with a 409 while it still has Products. */
export function useDeleteCategory() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (slug: string) =>
      api<void>('catalog', `/categories/${encodeURIComponent(slug)}`, { method: 'DELETE', token }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: categoriesKey }),
  })
}
