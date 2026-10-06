import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Page } from '../domain/paging'
import {
  type Eligibility,
  type RatingSummary,
  type Review,
  type ReviewDraft,
  reviewPayload,
  reviewsPageSize,
} from '../domain/reviews'
import { api } from './http'

const reviewsKey = ['reviews']

/** The Rating summaries of the Products on a page of the listing, in one request, keyed by SKU. */
export function useRatingSummaries(skus: string[]) {
  const query = skus.map((sku) => `sku=${encodeURIComponent(sku)}`).join('&')
  return useQuery({
    queryKey: [...reviewsKey, 'summaries', query],
    queryFn: async () => {
      const summaries = await api<RatingSummary[]>('reviews-ratings', `/rating-summaries?${query}`)
      return Object.fromEntries(summaries.map((s) => [s.sku, s])) as Record<string, RatingSummary | undefined>
    },
    enabled: skus.length > 0,
    placeholderData: keepPreviousData,
  })
}

export function useRatingSummary(sku: string) {
  return useQuery({
    queryKey: [...reviewsKey, sku, 'summary'],
    queryFn: () => api<RatingSummary>('reviews-ratings', `/products/${encodeURIComponent(sku)}/rating-summary`),
  })
}

/** A page of the Product's reviews, newest first, counting from 0. */
export function useReviews(sku: string, page: number) {
  return useQuery({
    queryKey: [...reviewsKey, sku, 'page', page],
    queryFn: () =>
      api<Page<Review>>(
        'reviews-ratings',
        `/products/${encodeURIComponent(sku)}/reviews?page=${page}&size=${reviewsPageSize}`,
      ),
    placeholderData: keepPreviousData,
  })
}

/** Whether the signed-in Customer may review the Product; disabled until they sign in. */
export function useEligibility(sku: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...reviewsKey, sku, 'eligibility'],
    queryFn: () => api<Eligibility>('reviews-ratings', `/products/${encodeURIComponent(sku)}/eligibility`, { token }),
    enabled: token !== undefined,
  })
}

/** Posts, edits or deletes the Customer's review; afterwards the Product's reviews, summary and eligibility reload. */
function useReviewMutation<T>(sku: string, mutationFn: (token: string | undefined, variables: T) => Promise<unknown>) {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (variables: T) => mutationFn(token, variables),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: [...reviewsKey, sku] }),
        queryClient.invalidateQueries({ queryKey: [...reviewsKey, 'summaries'] }),
      ]),
  })
}

export function usePostReview(sku: string) {
  return useReviewMutation(sku, (token, draft: ReviewDraft) =>
    api<Review>('reviews-ratings', `/products/${encodeURIComponent(sku)}/reviews`, {
      method: 'POST',
      body: reviewPayload(draft),
      token,
    }),
  )
}

export function useEditReview(sku: string) {
  return useReviewMutation(sku, (token, { id, draft }: { id: string; draft: ReviewDraft }) =>
    api<Review>('reviews-ratings', `/reviews/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: reviewPayload(draft),
      token,
    }),
  )
}

export function useDeleteReview(sku: string) {
  return useReviewMutation(sku, (token, id: string) =>
    api<void>('reviews-ratings', `/reviews/${encodeURIComponent(id)}`, { method: 'DELETE', token }),
  )
}
