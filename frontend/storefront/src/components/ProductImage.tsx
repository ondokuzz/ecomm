import { useState } from 'react'
import { type Product, productImage } from '../domain/catalog'
import { Icon } from './ui/Icon'
import { cx } from './ui/cx'

/** A Product's first image, or a placeholder when it has none or the file fails to load. */
export function ProductImage({ product, className }: { product: Product; className?: string }) {
  const src = productImage(product)
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
