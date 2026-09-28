import { Link } from 'react-router'
import type { PricedCart } from '../domain/cart'
import { formatMoney } from '../domain/money'
import { ProductImage } from './ProductImage'
import { Button } from './ui/Button'
import { Icon } from './ui/Icon'
import { QuantityStepper } from './ui/QuantityStepper'
import { cx } from './ui/cx'

/**
 * A priced Cart's lines, each with its Product's thumbnail and name. With `onChange` and
 * `onRemove`, the Customer can change a quantity or remove a line; without, it is `compact`
 * enough for the checkout summary.
 */
export function CartLines({
  priced,
  onChange,
  onRemove,
  disabled,
  compact,
}: {
  priced: PricedCart
  onChange?: (variantId: string, quantity: number) => void
  onRemove?: (variantId: string) => void
  disabled?: boolean
  compact?: boolean
}) {
  return (
    <ul className={cx('cart-lines', compact && 'compact')} aria-label="Cart lines">
      {priced.lines.map((line) => {
        // The Variant ID stands in while the Product loads, or when Catalog doesn't have it.
        const name = line.name ?? line.variantId
        const href = `/products/${encodeURIComponent(line.variantId)}`
        return (
          <li key={line.variantId} className="cart-line">
            <Link to={href} className="cart-line-thumb" tabIndex={-1} aria-hidden="true">
              {line.product ? (
                <ProductImage product={line.product} />
              ) : (
                <div className="product-image product-image-placeholder">
                  <Icon name="image" size={24} />
                </div>
              )}
            </Link>
            <div className="cart-line-info">
              {line.product?.attributes.brand && <span className="brand-name">{line.product.attributes.brand}</span>}
              <Link to={href} className="cart-line-name">
                {name}
              </Link>
              <span className="muted">
                {compact && `${line.quantity} × `}
                {line.unitPrice ? formatMoney(line.unitPrice) : 'Price unavailable'}
                {!compact && line.unitPrice && ' each'}
              </span>
            </div>
            {!compact && onChange && (
              <QuantityStepper
                // Remount when the Cart changes, so the field shows the quantity the Cart now holds.
                key={line.quantity}
                value={line.quantity}
                onChange={(quantity) => onChange(line.variantId, quantity)}
                disabled={disabled}
                label={`Quantity of ${name}`}
              />
            )}
            <span className="cart-line-total price">{line.lineTotal ? formatMoney(line.lineTotal) : '—'}</span>
            {!compact && onRemove && (
              <Button
                variant="ghost"
                icon
                className="cart-line-remove"
                aria-label={`Remove ${name}`}
                title="Remove"
                onClick={() => onRemove(line.variantId)}
                disabled={disabled}
              >
                <Icon name="trash" size={18} />
              </Button>
            )}
          </li>
        )
      })}
    </ul>
  )
}

