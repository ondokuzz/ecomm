import type { ReactNode } from 'react'
import { EmptyState } from './Status'
import { ButtonLink } from './ui/Button'
import { Icon } from './ui/Icon'

/** The Not Found page: for an unknown route, and for a Product or Order the services don't have. */
export function NotFound({
  title = 'Page not found',
  children = 'There is nothing at this address.',
  back = { label: 'Back to products', to: '/' },
}: {
  title?: string
  children?: ReactNode
  back?: { label: string; to: string }
}) {
  return (
    <EmptyState
      title={title}
      illustration={
        <span className="empty-icon">
          <Icon name="search" size={28} />
        </span>
      }
      action={
        <ButtonLink variant="primary" to={back.to}>
          {back.label}
        </ButtonLink>
      }
    >
      {children}
    </EmptyState>
  )
}
