import { createContext, useContext } from 'react'

/** At most this many toasts show at once; a new one pushes out the oldest. */
export const maxToasts = 3

/** The toasts with `toast` added last, dropping the oldest beyond `maxToasts`. */
export function addToast(toasts: Toast[], toast: Toast): Toast[] {
  return [...toasts, toast].slice(-maxToasts)
}

/** The toasts without the one with `id`. */
export function dismissToast(toasts: Toast[], id: number): Toast[] {
  return toasts.filter((t) => t.id !== id)
}

/** A toast as the Storefront shows it: a message, and optionally a link on to what it's about. */
export interface Toast {
  id: number
  message: string
  action?: { label: string; to: string }
}

/** A toast to show; the Toaster gives it its ID. */
export type NewToast = Omit<Toast, 'id'>

/** Shows a toast; `Toaster` provides it. */
export const ShowToast = createContext<((toast: NewToast) => void) | undefined>(undefined)

/** Shows a short-lived message, announced to screen readers, without moving focus. */
export function useToast(): (toast: NewToast) => void {
  const show = useContext(ShowToast)
  if (!show) throw new Error('useToast needs a Toaster above it')
  return show
}
