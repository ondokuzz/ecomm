import { Link } from 'react-router'
import type { PricedCart } from '../domain/cart'
import { formatMoney } from '../domain/money'
import { parseQuantity } from '../domain/quantity'
import { Button } from './ui/Button'
import { Card } from './ui/Card'
import { Input } from './ui/Input'

/** A priced Cart as a table. With `onChange`/`onRemove`, the Customer can edit it. */
export function CartLines({
  priced,
  onChange,
  onRemove,
  disabled,
}: {
  priced: PricedCart
  onChange?: (variantId: string, quantity: number) => void
  onRemove?: (variantId: string) => void
  disabled?: boolean
}) {
  const editable = onChange !== undefined
  return (
    <Card flush className="table-scroll">
      <table className="lines">
        <thead>
          <tr>
            <th>Product</th>
            <th className="num">Price</th>
            <th className="num">Quantity</th>
            <th className="num">Total</th>
            {editable && <th />}
          </tr>
        </thead>
        <tbody>
          {priced.lines.map((line) => (
            <tr key={line.variantId}>
              <td>
                <Link to={`/products/${encodeURIComponent(line.variantId)}`}>{line.name ?? line.variantId}</Link>
              </td>
              <td className="num">{line.unitPrice ? formatMoney(line.unitPrice) : '—'}</td>
              <td className="num">
                {editable ? (
                  <Input
                    // Remount when the Cart changes, so the field shows the quantity the Cart now holds.
                    key={line.quantity}
                    type="number"
                    min={1}
                    aria-label={`Quantity of ${line.name ?? line.variantId}`}
                    defaultValue={line.quantity}
                    disabled={disabled}
                    onBlur={(e) => {
                      const quantity = parseQuantity(e.target.value, { min: 1 })
                      if (quantity !== undefined && quantity !== line.quantity) onChange(line.variantId, quantity)
                      else e.target.value = String(line.quantity)
                    }}
                  />
                ) : (
                  line.quantity
                )}
              </td>
              <td className="num">{line.lineTotal ? formatMoney(line.lineTotal) : '—'}</td>
              {editable && (
                <td>
                  <Button variant="ghost" size="sm" onClick={() => onRemove?.(line.variantId)} disabled={disabled}>
                    Remove
                  </Button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th colSpan={3}>Total</th>
            <th className="num">{priced.total ? formatMoney(priced.total) : '—'}</th>
            {editable && <th />}
          </tr>
        </tfoot>
      </table>
    </Card>
  )
}
