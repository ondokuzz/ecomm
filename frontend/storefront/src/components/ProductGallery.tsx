import { useState } from 'react'
import { type Product, type Variant, galleryImages } from '../domain/catalog'
import { ProductImage } from './ProductImage'

/**
 * The Product page's images: the one shown large on its stage, and below it a thumbnail of each
 * when there is more than one, the chosen Variant's own first.
 */
export function ProductGallery({ product, variant }: { product: Product; variant: Variant }) {
  const images = galleryImages(product, variant)
  const [shown, setShown] = useState(0)
  return (
    <div className="product-media gallery">
      <div className="gallery-stage">
        <ProductImage product={{ name: product.name, images: images.slice(shown, shown + 1) }} />
      </div>
      {images.length > 1 && (
        <ul className="gallery-thumbs" aria-label="Product images">
          {images.map((src, i) => (
            <li key={src}>
              <button
                type="button"
                className="gallery-thumb"
                aria-label={`Image ${i + 1} of ${images.length}`}
                aria-pressed={i === shown}
                onClick={() => setShown(i)}
              >
                <img src={src} alt="" width={96} height={72} decoding="async" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
