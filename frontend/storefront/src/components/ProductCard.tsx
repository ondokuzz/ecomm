import { Link } from 'react-router'
import { useFormatMoney } from '../api/currencies'
import type { ProductCardInfo } from '../domain/catalog'
import type { RatingSummary } from '../domain/reviews'
import { ProductImage } from './ProductImage'
import { SummaryStars } from './StarRating'
import { Badge } from './ui/Badge'
import { Card } from './ui/Card'
import { Icon } from './ui/Icon'

/**
 * A Product as a card in a grid, leading to its page, with its Rating summary once known, and a
 * `label` saying why it is shown, such as "Popular in Audio", if it is given one.
 */
export function ProductCard({
  product,
  summary,
  label,
}: {
  product: ProductCardInfo
  summary: RatingSummary | undefined
  label?: string
}) {
  const formatMoney = useFormatMoney()
  return (
    <li>
      <Link to={`/products/${encodeURIComponent(product.sku)}`} className="card-link">
        <Card interactive className="product-card">
          <div className="product-card-media">
            <ProductImage product={{ name: product.name, images: product.image ? [product.image] : [] }} />
            {product.inStock === false && <Badge className="product-card-stock">Out of stock</Badge>}
          </div>
          {label && (
            <Badge tone="info" className="product-card-label">
              {label}
            </Badge>
          )}
          <span className="product-brand">{product.attributes.brand}</span>
          <span className="product-name">{product.name}</span>
          <SummaryStars summary={summary} />
          <span className="product-card-footer">
            <span className="price">
              {product.priceVaries && <span className="price-from">From </span>}
              {formatMoney(product.priceFrom)}
            </span>
            <span className="product-card-go" aria-hidden="true">
              <Icon name="arrowRight" size={18} />
            </span>
          </span>
        </Card>
      </Link>
    </li>
  )
}
