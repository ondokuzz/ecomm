import { useId, useState } from 'react'
import type { FieldErrors } from '../api/fieldErrors'

/**
 * The messages an editor shows beside its fields, by the field names Promotions or Catalog use,
 * such as `discount.amountOff.currency`. A message stays until its field is edited.
 */
export function useFieldErrors() {
  const [errors, setErrors] = useState<FieldErrors>({})
  const id = useId()
  const errorId = (name: string) => `${id}-${name.replace(/[^\w-]/g, '-')}-error`
  /** The attributes that mark a control invalid and tie it to its message. */
  const field = (...names: string[]) => {
    const named = names.find((name) => errors[name])
    return {
      'aria-invalid': named ? true : undefined,
      'aria-describedby': named ? errorId(named) : undefined,
    }
  }
  const clearError = (...names: string[]) =>
    setErrors((current) => {
      if (!names.some((name) => name in current)) return current
      return Object.fromEntries(Object.entries(current).filter(([name]) => !names.includes(name)))
    })
  return { errors, setErrors, errorId, field, clearError }
}

export type FieldErrorsState = ReturnType<typeof useFieldErrors>
