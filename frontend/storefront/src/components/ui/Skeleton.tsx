import type { CSSProperties } from 'react'
import { cx } from './cx'

/** A shimmering placeholder the shape of content that is still loading. */
export function Skeleton({
  width = '100%',
  height = '1rem',
  radius,
  className,
}: {
  width?: CSSProperties['width']
  height?: CSSProperties['height']
  radius?: CSSProperties['borderRadius']
  className?: string
}) {
  return (
    <span className={cx('skeleton', className)} style={{ width, height, borderRadius: radius }} aria-hidden="true" />
  )
}
