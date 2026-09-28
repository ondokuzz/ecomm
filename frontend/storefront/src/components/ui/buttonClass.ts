import { cx } from './cx'

export type Variant = 'primary' | 'secondary' | 'ghost' | 'outline'
export type Size = 'sm' | 'md' | 'lg'

export interface Look {
  variant?: Variant
  size?: Size
  /** Square, for a button that holds only an icon; give it an `aria-label`. */
  icon?: boolean
}

/** The classes that make an element look like a Button, for elements Button and ButtonLink don't cover. */
export function buttonClass({ variant = 'outline', size = 'md', icon }: Look, className?: string) {
  return cx(
    'btn',
    variant !== 'outline' && `btn-${variant}`,
    size !== 'md' && `btn-${size}`,
    icon && 'btn-icon',
    className,
  )
}
