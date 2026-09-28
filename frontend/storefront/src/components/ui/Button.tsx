import type { ButtonHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router'
import { cx } from './cx'

type Variant = 'primary' | 'secondary' | 'ghost' | 'outline'
type Size = 'sm' | 'md' | 'lg'

interface Look {
  variant?: Variant
  size?: Size
  /** Square, for a button that holds only an icon; give it an `aria-label`. */
  icon?: boolean
}

function buttonClass({ variant = 'outline', size = 'md', icon }: Look, className?: string) {
  return cx(
    'btn',
    variant !== 'outline' && `btn-${variant}`,
    size !== 'md' && `btn-${size}`,
    icon && 'btn-icon',
    className,
  )
}

export function Button({
  variant,
  size,
  icon,
  loading = false,
  className,
  disabled,
  children,
  type = 'button',
  ...props
}: Look &
  ButtonHTMLAttributes<HTMLButtonElement> & {
    /** Shows a spinner and disables the button while its action runs. */
    loading?: boolean
  }) {
  return (
    <button
      {...props}
      type={type}
      className={buttonClass({ variant, size, icon }, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
    >
      {loading && <span className="spinner" aria-hidden="true" />}
      {children}
    </button>
  )
}

/** A link that looks like a Button. */
export function ButtonLink({ variant, size, icon, className, ...props }: Look & LinkProps) {
  return <Link {...props} className={buttonClass({ variant, size, icon }, className)} />
}
