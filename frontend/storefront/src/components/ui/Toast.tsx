import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Button } from './Button'
import { Icon } from './Icon'
import { type NewToast, ShowToast, type Toast, addToast, dismissToast } from './toasts'

/** How long a toast stays up, unless a pointer rests on it or focus is inside it. */
const toastDuration = 5000

/**
 * Provides `useToast` to everything under it, and shows the toasts in the corner. They sit in a
 * polite live region that is always there, so screen readers announce each one as it arrives,
 * and nothing in them takes focus.
 */
export function Toaster({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(0)
  const show = useCallback(
    (toast: NewToast) => setToasts((current) => addToast(current, { ...toast, id: ++nextId.current })),
    [],
  )
  const dismiss = useCallback((id: number) => setToasts((current) => dismissToast(current, id)), [])

  return (
    <ShowToast value={show}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((toast) => (
          <ToastCard key={toast.id} toast={toast} onDismiss={dismiss} />
        ))}
      </div>
    </ShowToast>
  )
}

function ToastCard({ toast, onDismiss }: { toast: Toast; onDismiss: (id: number) => void }) {
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const paused = hovered || focused

  // The countdown starts over once the pointer or focus leaves.
  useEffect(() => {
    if (paused) return
    const timer = setTimeout(() => onDismiss(toast.id), toastDuration)
    return () => clearTimeout(timer)
  }, [paused, onDismiss, toast.id])

  return (
    // The smoke test finds the toast by its `toast` class.
    <div
      className="toast"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocus={() => setFocused(true)}
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget)) setFocused(false)
      }}
    >
      <span className="toast-icon">
        <Icon name="check" size={16} />
      </span>
      <span className="toast-message">{toast.message}</span>
      {toast.action && (
        <Link to={toast.action.to} className="toast-action" onClick={() => onDismiss(toast.id)}>
          {toast.action.label}
        </Link>
      )}
      <Button variant="ghost" icon size="sm" aria-label="Dismiss" onClick={() => onDismiss(toast.id)}>
        <Icon name="close" size={16} />
      </Button>
    </div>
  )
}
