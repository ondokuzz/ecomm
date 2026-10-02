import { useQuery } from '@tanstack/react-query'
import { type Currencies, type Currency, currenciesOf } from '../domain/money'
import { api } from './http'

/** The currencies Catalog prices in, by code. They change only with Catalog's JDK, so they are fetched once. */
export function useCurrencies() {
  return useQuery({
    queryKey: ['currencies'],
    queryFn: () => api<Currency[]>('catalog', '/currencies'),
    select: (list): Currencies => currenciesOf(list),
    staleTime: Infinity,
  })
}
