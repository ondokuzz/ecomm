import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { TemplatePreview } from '../domain/template'
import { api } from './http'

/** Every Message type on every Notification channel, previewed with its sample content. Staff only. */
export function useTemplatePreviews() {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ['templates'],
    queryFn: () => api<TemplatePreview[]>('notifications', '/templates', { token }),
  })
}
