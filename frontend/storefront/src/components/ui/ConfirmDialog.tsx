import { useEffect, useId, useRef } from 'react'
import { Button } from './Button'

/**
 * Asks the Customer to confirm an action, in a native modal `<dialog>`: the browser keeps focus
 * inside it, closes it on Escape, and returns focus to where it was. Cancel has focus first, so
 * Enter never confirms by accident.
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  confirming,
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  children?: string
  confirmLabel: string
  /** Shows a spinner on the confirm button while the action runs. */
  confirming?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const element = dialog.current
    if (!element) return
    if (open && !element.open) element.showModal()
    if (!open && element.open) element.close()
  }, [open])

  return (
    <dialog
      ref={dialog}
      className="dialog"
      aria-labelledby={titleId}
      // Escape closes the dialog itself, but not while the action runs; keep `open` in step.
      onCancel={(e) => confirming && e.preventDefault()}
      onClose={() => open && onCancel()}
      onClick={(e) => {
        // The body fills the dialog, so a click on the dialog element itself is on the backdrop.
        if (e.target === e.currentTarget && !confirming) onCancel()
      }}
    >
      <div className="dialog-body">
        <h2 id={titleId}>{title}</h2>
        {children && <p className="muted">{children}</p>}
        <div className="dialog-actions">
          <Button autoFocus onClick={onCancel} disabled={confirming}>
            Cancel
          </Button>
          <Button variant="danger" onClick={onConfirm} loading={confirming}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </dialog>
  )
}
