import type { HTMLAttributes } from 'react'
import { cx } from './cx'

export type Tone = 'neutral' | 'primary' | 'success' | 'danger' | 'warning' | 'info'

/** A small pill of status or count; `dot` leads it with a dot in its colour. */
export function Badge({
  tone = 'neutral',
  dot,
  className,
  ...props
}: HTMLAttributes<HTMLSpanElement> & { tone?: Tone; dot?: boolean }) {
  return (
    <span {...props} className={cx('badge', tone !== 'neutral' && `badge-${tone}`, dot && 'badge-dot', className)} />
  )
}
