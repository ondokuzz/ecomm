import { Link } from 'react-router'
import { useFormatMoney } from '../api/currencies'
import type { VariantDetail } from '../domain/catalog'
import type { Money } from '../domain/money'
import { ProductThumb } from './ProductImage'
import { Button } from './ui/Button'
import { Icon } from './ui/Icon'
import { QuantityStepper } from './ui/QuantityStepper'
import { cx } from './ui/cx'

/** A Cart line or an Order Line: a Variant and its quantity, with its Product and Price once known. */
export interface ProductLine {
  variantId: string
  quantity: number
  variant?: VariantDetail
  name?: string
  unitPrice?: Money
  lineTotal?: Money
}

/**
 * A Cart's or an Order's lines, each with its Product's thumbnail and name, in a list named
 * `label`. With `onChange` and `onRemove`, the Customer can change a quantity or remove a line;
 * without, it is `compact` enough for a summary.
 */
export function ProductLines({
  lines,
  label,
  onChange,
  onRemove,
  disabled,
  compact,
}: {
  lines: ProductLine[]
  label: string
  onChange?: (variantId: string, quantity: number) => void
  onRemove?: (variantId: string) => void
  disabled?: boolean
  compact?: boolean
}) {
  const formatMoney = useFormatMoney()
  return (
    <ul className={cx('cart-lines', compact && 'compact')} aria-label={label}>
      {lines.map((line) => {
        // The Variant ID stands in while the Variant loads, or when Catalog doesn't have it.
        const name = line.name ?? line.variantId
        const href = variantHref(line)
        return (
          <li key={line.variantId} className="cart-line">
            <Link to={href} className="cart-line-thumb" tabIndex={-1} aria-hidden="true">
              <ProductThumb variant={line.variant} />
            </Link>
            <div className="cart-line-info">
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

/** The line's Product page with its Variant chosen; by Variant ID, a first Variant's SKU, while the Variant is unknown. */
function variantHref({ variantId, variant }: ProductLine): string {
  if (!variant) return `/products/${encodeURIComponent(variantId)}`
  return `/products/${encodeURIComponent(variant.product.sku)}?${new URLSearchParams({ variant: variantId })}`
}
