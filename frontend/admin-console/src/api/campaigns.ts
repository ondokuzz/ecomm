import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { type Campaign, type CampaignRequest, switched } from '../domain/campaign'
import { api } from './http'

const campaignsKey = ['campaigns'] as const

/** Every Campaign by priority, each with its state as Promotions works it out now. Staff only. */
export function useCampaigns() {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: campaignsKey,
    queryFn: () => api<Campaign[]>('promotions', '/campaigns', { token }),
  })
}

export function useCampaign(id: string | undefined) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...campaignsKey, id],
    queryFn: () => api<Campaign>('promotions', `/campaigns/${encodeURIComponent(id!)}`, { token }),
    enabled: id !== undefined,
  })
}

/** Creates a Campaign, or replaces every field of the one with `id`. */
export function useSaveCampaign(id: string | undefined) {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CampaignRequest) =>
      id === undefined
        ? api<Campaign>('promotions', '/campaigns', { method: 'POST', body: request, token })
        : api<Campaign>('promotions', `/campaigns/${encodeURIComponent(id)}`, { method: 'PUT', body: request, token }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: campaignsKey }),
  })
}

export function useDeleteCampaign() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      api<void>('promotions', `/campaigns/${encodeURIComponent(id)}`, { method: 'DELETE', token }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: campaignsKey }),
  })
}

/** Switches a Campaign on or off, leaving the rest of it as it is. */
export function useSwitchCampaign() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ campaign, active }: { campaign: Campaign; active: boolean }) =>
      api<Campaign>('promotions', `/campaigns/${encodeURIComponent(campaign.id)}`, {
        method: 'PUT',
        body: switched(campaign, active),
        token,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: campaignsKey }),
  })
}
