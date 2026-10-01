import { Component, type ReactNode } from 'react'
import { useLocation } from 'react-router'
import { ErrorPanel } from './Status'
import { Button, ButtonLink } from './ui/Button'

/**
 * Catches a page that fails to render, so the Customer sees what happened and a way on instead of a
 * blank page; the header and footer stay. Moving to another page clears it.
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
      <ErrorPanel title="Something went wrong" message="This page ran into a problem. Please try again.">
        <div className="error-state-actions">
          <Button variant="primary" onClick={() => this.setState({ failed: false })}>
            Try again
          </Button>
          <ButtonLink to="/">Back to products</ButtonLink>
        </div>
      </ErrorPanel>
    )
  }
}
