import { useState } from 'react'
import { type Product, type Variant, type VariantDetail, productImage } from '../domain/catalog'
import { Icon } from './ui/Icon'
import { cx } from './ui/cx'

/**
 * A Product's image (its Variant's own, when `variant` has one), or a placeholder when it has none or
 * the file fails to load.
 */
export function ProductImage({
  product,
  variant,
  className,
}: {
  product: Pick<Product, 'name' | 'images'>
  variant?: Pick<Variant, 'images'>
  className?: string
}) {
  const src = productImage(product, variant)
  // The image that failed, so a different Product's image in the same place still gets its try.
  const [failed, setFailed] = useState<string>()

  if (!src || failed === src) {
    return (
      <div role="img" aria-label={product.name} className={cx('product-image', 'product-image-placeholder', className)}>
        <Icon name="image" size={40} />
      </div>
    )
  }
  return (
    <img
      src={src}
      alt={product.name}
      width={400}
      height={300}
      decoding="async"
      className={cx('product-image', className)}
      onError={() => setFailed(src)}
    />
  )
}

/**
 * A Variant's image as a thumbnail, or a placeholder while it loads or when Catalog doesn't have
 * it; `label` names the placeholder, such as by the Variant ID, where the thumbnail isn't decorative.
 */
export function ProductThumb({ variant, label }: { variant?: VariantDetail; label?: string }) {
  if (variant) return <ProductImage product={variant.product} variant={variant} />
  return (
    <div
      className="product-image product-image-placeholder"
      role={label ? 'img' : undefined}
      aria-label={label}
      title={label}
    >
      <Icon name="image" size={24} />
    </div>
  )
}
