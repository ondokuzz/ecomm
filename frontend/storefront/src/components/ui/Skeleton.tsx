import type { CSSProperties } from 'react'

/** A shimmering placeholder the shape of content that is still loading. */
export function Skeleton({
  width = '100%',
  height = '1rem',
  radius,
}: {
  width?: CSSProperties['width']
  height?: CSSProperties['height']
  radius?: CSSProperties['borderRadius']
}) {
  return <span className="skeleton" style={{ width, height, borderRadius: radius }} aria-hidden="true" />
}
