import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Page } from '../domain/paging'
import { type Action, type ModeratedReview, moderationPageSize } from '../domain/review'
import { api } from './http'

const reviewsKey = ['reviews'] as const

/**
 * A page of the moderation queue: held reviews and published ones with open reports, oldest first.
 * The page on show stays until the next arrives. Staff only.
 */
export function useModerationQueue(page: number) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...reviewsKey, 'queue', page],
    queryFn: () =>
      api<Page<ModeratedReview>>('reviews-ratings', `/moderation/reviews?page=${page}&size=${moderationPageSize}`, {
        token,
      }),
    placeholderData: keepPreviousData,
  })
}

/** Any review, whatever its status, with its reports and moderation history. Staff only. */
export function useModeratedReview(id: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...reviewsKey, id],
    queryFn: () => api<ModeratedReview>('reviews-ratings', `/moderation/reviews/${encodeURIComponent(id)}`, { token }),
  })
}

/**
 * Publishes, dismisses the reports of, or removes a review as the signed-in Staff member; a removal
 * takes its reason as `body`. Another Staff member's action first answers 409 with a `reason`:
 * `notHeld`, `noOpenReports` or `alreadyRemoved`.
 */
export function useModerate(id: string) {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ action, body }: { action: Action; body?: object }) =>
      api<ModeratedReview>('reviews-ratings', `/reviews/${encodeURIComponent(id)}/${action}`, {
        method: 'POST',
        body,
        token,
      }),
    onSuccess: (review) => {
      queryClient.setQueryData([...reviewsKey, id], review)
      return queryClient.invalidateQueries({ queryKey: [...reviewsKey, 'queue'] })
    },
    // Moderated elsewhere first: show it as it is now.
    onError: () => queryClient.invalidateQueries({ queryKey: [...reviewsKey, id] }),
  })
}
