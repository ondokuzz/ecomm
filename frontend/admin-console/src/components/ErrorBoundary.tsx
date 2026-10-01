import { Component, type ReactNode } from 'react'
import { useLocation } from 'react-router'
import { Button, ButtonLink } from './ui/Button'

/**
 * Catches a page that fails to render, so Staff see what happened and a way on instead of a blank
 * page; the header stays. Moving to another page clears it.
 */
export function RouteErrorBoundary({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  return <ErrorBoundary key={pathname}>{children}</ErrorBoundary>
}

class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: unknown) {
    console.error('A page failed to render', error)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div className="panel" role="alert">
        <h1>Something went wrong</h1>
        <p className="muted">This page ran into a problem. Please try again.</p>
        <div className="row">
          <Button variant="primary" onClick={() => this.setState({ failed: false })}>
            Try again
          </Button>
          <ButtonLink to="/categories">Back to Categories</ButtonLink>
        </div>
      </div>
    )
  }
}
