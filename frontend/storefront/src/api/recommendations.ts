import { useQuery } from '@tanstack/react-query'
import type { AlsoBought } from '../domain/recommendations'
import { api } from './http'

/** How many Products the "Customers also bought" row shows. */
const alsoBoughtLimit = 4

/** The Products bought most often together with the Product, from Recommendations. */
export function useAlsoBought(sku: string) {
  return useQuery({
    queryKey: ['recommendations', sku, 'also-bought'],
    queryFn: () =>
      api<AlsoBought>('recommendations', `/products/${encodeURIComponent(sku)}/also-bought?limit=${alsoBoughtLimit}`),
  })
}
