import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Preferences, PreferencesRequest } from '../domain/preferences'
import { api } from './http'

const preferencesKey = ['preferences']

/** The Customer's Notification preferences: email only until they save their own. */
export function usePreferences() {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: preferencesKey,
    queryFn: () => api<Preferences>('notifications', '/preferences/me', { token }),
    enabled: token !== undefined,
  })
}

/** Saves them, in place of the last; saving the same again changes nothing, so no Idempotency-Key. */
export function useSavePreferences() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: PreferencesRequest) =>
      api<Preferences>('notifications', '/preferences/me', { method: 'PUT', body, token }),
    onSuccess: (saved) => queryClient.setQueryData(preferencesKey, saved),
  })
}
