import type { InputHTMLAttributes } from 'react'
import { cx } from './cx'

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={cx('input', className)} />
}
