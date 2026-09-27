import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from 'react-oidc-context'
import { useCart } from '../api/cart'
import { useAuthPending, useSignin, useSignout } from '../auth/session'
import { itemCount } from '../domain/cart'

export function Layout() {
  return (
    <>
      <header className="site-header">
        <Link to="/" className="brand">
          Ecomm
        </Link>
        <nav>
          <NavLink to="/" end>
            Products
          </NavLink>
          <NavLink to="/cart">
            Cart <CartBadge />
          </NavLink>
          <NavLink to="/orders">My Orders</NavLink>
        </nav>
        <CustomerMenu />
      </header>
      <main>
        <Outlet />
      </main>
    </>
  )
}

function CartBadge() {
  const cart = useCart()
  const count = cart.data ? itemCount(cart.data) : 0
  return count > 0 ? <span className="badge">{count}</span> : null
}

/** Who is signed in, or the way to sign in or register. */
function CustomerMenu() {
  const auth = useAuth()
  const pending = useAuthPending()
  const { signin, register } = useSignin()
  const signout = useSignout()

  if (pending) return <div className="customer-menu" />
  if (auth.user) {
    return (
      <div className="customer-menu">
        <span className="muted">{auth.user.profile.email ?? auth.user.profile.preferred_username}</span>
        <button onClick={signout}>Log out</button>
      </div>
    )
  }
  return (
    <div className="customer-menu">
      <button onClick={signin}>Log in</button>
      <button className="primary" onClick={register}>
        Register
      </button>
    </div>
  )
}
