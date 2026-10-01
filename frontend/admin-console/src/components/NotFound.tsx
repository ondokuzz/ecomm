import { ButtonLink } from './ui/Button'

export function NotFound({ what = 'page' }: { what?: string }) {
  return (
    <div className="panel">
      <h1>Not found</h1>
      <p className="muted">There's no {what} here. It may have been deleted, or the address is wrong.</p>
      <ButtonLink to="/categories">Back to Categories</ButtonLink>
    </div>
  )
}
