import { useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link, NavLink, Outlet } from 'react-router'
import { useSignout } from '../auth/session'
import { RouteErrorBoundary } from './ErrorBoundary'
import { Button } from './ui/Button'
import { Icon } from './ui/Icon'

/** The console's frame: a slim header with its sections and the Staff member, and the page below. */
export function Layout() {
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <div className="site-header-inner">
          <Link to="/" className="brand" aria-label="Ecomm Admin home">
            <span className="brand-mark" aria-hidden="true">
              e
            </span>
            Ecomm <span className="brand-tag">Admin</span>
          </Link>
          <nav className="site-nav" aria-label="Sections">
            <NavLink to="/products" className="nav-link">
              Products
            </NavLink>
            <NavLink to="/categories" className="nav-link">
              Categories
            </NavLink>
          </nav>
          <Account />
        </div>
      </header>
      <main id="main" className="page">
        <RouteErrorBoundary>
          <Outlet />
        </RouteErrorBoundary>
      </main>
    </>
  )
}

function Account() {
  const auth = useAuth()
  const signout = useSignout()
  const [signingOut, setSigningOut] = useState(false)
  return (
    <div className="account">
      <span className="account-email">{auth.user?.profile.email}</span>
      <Button
        variant="ghost"
        size="sm"
        loading={signingOut}
        onClick={() => {
          setSigningOut(true)
          signout().catch(() => setSigningOut(false))
        }}
      >
        <Icon name="logout" size={16} />
        Sign out
      </Button>
    </div>
  )
}
