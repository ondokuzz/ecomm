import type { HTMLAttributes } from 'react'
import { cx } from './cx'

/** A raised surface. `interactive` lifts it on hover when it sits inside a `.card-link`. */
export function Card({
  interactive,
  flush,
  className,
  ...props
}: HTMLAttributes<HTMLDivElement> & { interactive?: boolean; flush?: boolean }) {
  return <div {...props} className={cx('card', interactive && 'card-interactive', flush && 'card-flush', className)} />
}
