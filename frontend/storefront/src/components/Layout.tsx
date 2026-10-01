import { useRef } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from 'react-oidc-context'
import { useCart } from '../api/cart'
import { useAuthPending, useSignin, useSignout } from '../auth/session'
import { itemCount } from '../domain/cart'
import { customerInitials, customerLabel } from '../domain/customer'
import { RouteErrorBoundary } from './ErrorBoundary'
import { Button } from './ui/Button'
import { buttonClass } from './ui/buttonClass'
import { Icon } from './ui/Icon'
import { cx } from './ui/cx'

const navLinks = [
  { to: '/', label: 'Products', end: true },
  { to: '/orders', label: 'My Orders', end: false },
]

export function Layout() {
  return (
    <>
      <header className="site-header">
        <div className="site-header-inner">
          <Brand />
          <nav className="site-nav" aria-label="Main">
            {navLinks.map((link) => (
              <NavLink key={link.to} to={link.to} end={link.end} className="nav-link">
                {link.label}
              </NavLink>
            ))}
          </nav>
          <div className="header-actions">
            <CartLink />
            <CustomerMenu />
            <MobileNav />
          </div>
        </div>
      </header>
      <main>
        <RouteErrorBoundary>
          <Outlet />
        </RouteErrorBoundary>
      </main>
      <Footer />
    </>
  )
}

function Brand() {
  return (
    <Link to="/" className="brand" aria-label="Ecomm home">
      <span className="brand-mark" aria-hidden="true">
        e
      </span>
      Ecomm
    </Link>
  )
}

/** The Cart icon, with the Cart's count popping in whenever it changes. */
function CartLink() {
  const cart = useCart()
  const count = cart.data ? itemCount(cart.data) : 0
  return (
    <NavLink
      to="/cart"
      className={({ isActive }) => buttonClass({ variant: 'ghost', icon: true }, cx('cart-link', isActive && 'active'))}
    >
      <Icon name="cart" size={22} />
      <span className="visually-hidden">Cart{count > 0 && ` (${count})`}</span>
      {count > 0 && (
        // Keyed by the count, so the pop animation runs again on each change.
        <span key={count} className="cart-count" aria-hidden="true">
          {count}
        </span>
      )}
    </NavLink>
  )
}

/** Who is signed in, behind their avatar; or the way to sign in or register. */
function CustomerMenu() {
  const auth = useAuth()
  const pending = useAuthPending()
  const signout = useSignout()
  const menu = useRef<HTMLDivElement>(null)

  if (pending) return null
  if (!auth.user) {
    return (
      <div className="row signed-out-actions">
        <SignInButtons />
      </div>
    )
  }

  const profile = auth.user.profile
  return (
    <>
      <button
        className="avatar"
        popoverTarget="customer-menu"
        aria-label="Customer menu"
        title={customerLabel(profile)}
      >
        {customerInitials(profile)}
      </button>
      <div id="customer-menu" ref={menu} popover="auto" className="popover-panel">
        <div className="menu-header">
          <small>Signed in as</small>
          <strong>{customerLabel(profile)}</strong>
        </div>
        <Link
          to="/orders"
          className={buttonClass({ variant: 'ghost' }, 'menu-item')}
          onClick={() => menu.current?.hidePopover()}
        >
          <Icon name="orders" size={18} />
          My Orders
        </Link>
        <Button variant="ghost" className="menu-item" onClick={signout}>
          <Icon name="logout" size={18} />
          Log out
        </Button>
      </div>
    </>
  )
}

/** On small screens the nav, and signing in, move behind a menu button. */
function MobileNav() {
  const auth = useAuth()
  const pending = useAuthPending()
  const panel = useRef<HTMLElement>(null)
  const close = () => panel.current?.hidePopover()

  return (
    <>
      <Button variant="ghost" icon className="menu-toggle" popoverTarget="mobile-nav" aria-label="Menu">
        <Icon name="menu" size={22} />
      </Button>
      <nav
        id="mobile-nav"
        ref={panel}
        popover="auto"
        className="popover-panel mobile-nav"
        aria-label="Main, small screens"
      >
        {navLinks.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.end}
            className={({ isActive }) => buttonClass({ variant: isActive ? 'secondary' : 'ghost' }, 'menu-item')}
            onClick={close}
          >
            {link.label}
          </NavLink>
        ))}
        {!pending && !auth.user && <SignInButtons />}
      </nav>
    </>
  )
}

/** Sign-in and registration both happen on Keycloak's hosted pages. */
function SignInButtons() {
  const { signin, register } = useSignin()
  return (
    <>
      <Button variant="ghost" onClick={signin}>
        Log in
      </Button>
      <Button variant="primary" onClick={register}>
        Register
      </Button>
    </>
  )
}

function Footer() {
  return (
    <footer className="site-footer">
      <div className="site-footer-inner">
        <div className="footer-brand">
          <Brand />
          <span className="muted">A demo store. Payments are mocked, nothing ships.</span>
        </div>
        <nav aria-label="Footer">
          <Link to="/">All products</Link>
          <Link to="/cart">Your cart</Link>
          <Link to="/orders">Your orders</Link>
        </nav>
      </div>
    </footer>
  )
}
