import { useQuery } from '@tanstack/react-query'
import { type Currency, type Money, currenciesOf, formatMoney } from '../domain/money'
import { api } from './http'

/** The Currencies Catalog prices in, by code. They change only with Catalog's JDK, so they are fetched once. */
export function useCurrencies() {
  return useQuery({
    queryKey: ['currencies'],
    queryFn: () => api<Currency[]>('catalog', '/currencies'),
    select: currenciesOf,
    staleTime: Infinity,
  })
}

/**
 * Shows Money by the Minor unit Catalog gives its Currency. Until Catalog's Currencies are in, or
 * for one it doesn't list, it shows a dash rather than a guess, which could be off a hundredfold.
 */
export function useFormatMoney(): (money: Money) => string {
  const currencies = useCurrencies().data
  return (money) => (currencies?.has(money.currency) ? formatMoney(money, currencies) : '—')
}
