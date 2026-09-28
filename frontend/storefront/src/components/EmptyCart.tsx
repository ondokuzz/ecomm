import { EmptyState } from './Status'
import { ButtonLink } from './ui/Button'

/** An empty Cart, with an illustration and a way back to the Products. */
export function EmptyCart({ children = 'Find something you like and add it here.' }: { children?: string }) {
  return (
    <EmptyState
      title="Your cart is empty"
      illustration={<EmptyCartIllustration />}
      action={
        <ButtonLink variant="primary" size="lg" to="/">
          Browse products
        </ButtonLink>
      }
    >
      {children}
    </EmptyState>
  )
}

/** A shopping cart with nothing in it but a couple of sparkles. */
function EmptyCartIllustration() {
  return (
    <svg viewBox="0 0 200 140" width="200" height="140" aria-hidden="true" className="empty-illustration">
      <ellipse cx="100" cy="126" rx="70" ry="8" className="empty-shadow" />
      <path d="M36 30h16l14 62h72l14-46H60" className="empty-cart" />
      <path d="M72 58h68 M76 74h58" className="empty-cart-rail" />
      <circle cx="76" cy="110" r="9" className="empty-cart-wheel" />
      <circle cx="132" cy="110" r="9" className="empty-cart-wheel" />
      <circle cx="160" cy="24" r="5" className="empty-spark" />
      <circle cx="30" cy="68" r="4" className="empty-spark-2" />
      <path d="M118 12l3.5 7 7 3.5-7 3.5-3.5 7-3.5-7-7-3.5 7-3.5z" className="empty-spark" />
    </svg>
  )
}
