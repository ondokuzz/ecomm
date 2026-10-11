import { Link } from 'react-router'
import { useFormatMoney } from '../api/currencies'
import type { ProductCardInfo } from '../domain/catalog'
import type { RatingSummary } from '../domain/reviews'
import { ProductImage } from './ProductImage'
import { SummaryStars } from './StarRating'
import { Badge } from './ui/Badge'
import { Card } from './ui/Card'

/** A Product as a card in a grid, leading to its page, with its Rating summary once known. */
export function ProductCard({ product, summary }: { product: ProductCardInfo; summary: RatingSummary | undefined }) {
  const formatMoney = useFormatMoney()
  return (
    <li>
      <Link to={`/products/${encodeURIComponent(product.sku)}`} className="card-link">
        <Card interactive className="product-card">
          <div className="product-card-media">
            <ProductImage product={{ name: product.name, images: product.image ? [product.image] : [] }} />
            {product.inStock === false && <Badge className="product-card-stock">Out of stock</Badge>}
          </div>
          <span className="brand-name">{product.attributes.brand}</span>
          <span className="product-name">{product.name}</span>
          <SummaryStars summary={summary} />
          <span className="price">
            {product.priceVaries && <span className="price-from">From </span>}
            {formatMoney(product.priceFrom)}
          </span>
        </Card>
      </Link>
    </li>
  )
}
