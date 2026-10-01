import { ApiError } from './http'

/** What a service said about each field of a refused request, by field name such as `attributes[1].values`. */
export type FieldErrors = Record<string, string>

/**
 * The field errors in a 400's problem detail, which names each offending field in `errors` as
 * `{field, message}`. Several messages about one field are joined. A failure that names no fields
 * has none, and is shown as a whole instead.
 */
export function fieldErrorsOf(error: unknown): FieldErrors {
  if (!(error instanceof ApiError) || !Array.isArray(error.problem.errors)) return {}
  const errors: FieldErrors = {}
  for (const entry of error.problem.errors as unknown[]) {
    if (!isFieldError(entry)) continue
    errors[entry.field] = errors[entry.field] ? `${errors[entry.field]}; ${entry.message}` : entry.message
  }
  return errors
}

function isFieldError(entry: unknown): entry is { field: string; message: string } {
  if (typeof entry !== 'object' || entry === null) return false
  const { field, message } = entry as Record<string, unknown>
  return typeof field === 'string' && typeof message === 'string'
}
