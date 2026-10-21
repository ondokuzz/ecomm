import { type FormEvent, useId, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/http'
import { useEligibility, useRequestRma } from '../api/rmas'
import {
  type KindOption,
  type LineEligibility,
  type RmaField,
  type RmaForm,
  type RmaRefusals,
  formatDay,
  kindOptionsOf,
  maxReason,
  rmaRefusalsOf,
  rmaRequestOf,
} from '../domain/rma'
import { ErrorMessage } from './Status'
import { Button } from './ui/Button'
import { Card } from './ui/Card'
import { Input } from './ui/Input'

/**
 * A delivered Order's lines, each with until when it may be returned and until when its warranty
 * runs, from Returns & Warranty, and "Return or claim warranty", which opens the request form under
 * it. Nothing shows while the lines load, or when Returns & Warranty can't be asked, as for the
 * Shipment.
 */
export function OrderReturns({ orderId, delivered }: { orderId: string; delivered: boolean }) {
  const eligibility = useEligibility(orderId, delivered).data
  const [open, setOpen] = useState<string>()
  const headingId = useId()
  if (!eligibility || eligibility.lines.length === 0) return null
  const now = new Date()

  return (
    <Card className="order-returns" role="region" aria-labelledby={headingId}>
      <h2 id={headingId} className="card-title">
        Returns and warranty
      </h2>
      <ul className="order-returns-lines" aria-label="Delivered products">
        {eligibility.lines.map((line) => {
          const name = line.productName ?? line.variantId
          const options = kindOptionsOf(line, now)
          // Nothing to claim: the dates below say why, so no form is offered only to be refused.
          const claimable = options.some((option) => option.available)
          return (
            <li key={line.variantId} className="order-returns-line">
              <div className="order-returns-top">
                <strong>{name}</strong>
                {claimable && open !== line.variantId && (
                  <Button
                    size="sm"
                    onClick={() => setOpen(line.variantId)}
                    aria-label={`Return or claim warranty: ${name}`}
                  >
                    Return or claim warranty
                  </Button>
                )}
              </div>
              <ul className="order-returns-dates muted">
                {options.map((option) => (
                  <li key={option.kind}>{optionSummary(option)}</li>
                ))}
              </ul>
              {open === line.variantId && (
                <RmaRequestForm orderId={orderId} line={line} onCancel={() => setOpen(undefined)} />
              )}
            </li>
          )
        })}
      </ul>
    </Card>
  )
}

/** "Return until 1 Nov 2026", or why it isn't open. */
function optionSummary(option: KindOption): string {
  if (option.available && option.endsAt) return `${option.label} until ${formatDay(option.endsAt)}`
  return `${option.label}: ${option.reason}`
}

/**
 * The request form for one delivered line: the kind, each with until when it is open or disabled
 * with the reason; the units, as checkboxes of the Serial numbers still free to claim, or a
 * quantity; and the reason. It keeps one `Idempotency-Key` while it is open, and shows each of
 * Returns & Warranty's refusals beside its field. Sent, it opens the new RMA's page.
 */
function RmaRequestForm({ orderId, line, onCancel }: { orderId: string; line: LineEligibility; onCancel: () => void }) {
  const id = useId()
  const navigate = useNavigate()
  const request = useRequestRma()
  const options = kindOptionsOf(line, new Date())
  const serialTracked = line.serialNumbers.length > 0
  const [form, setForm] = useState<RmaForm>(() => ({
    kind: options.find((option) => option.available)?.kind,
    serialNumbers: line.serialNumbers.length === 1 ? [...line.serialNumbers] : [],
    quantity: '1',
    reason: '',
  }))
  const [errors, setErrors] = useState<RmaRefusals>({})
  // A failure no field explains, shown as a whole until the form changes.
  const [failure, setFailure] = useState<unknown>()

  const update = (change: Partial<RmaForm>) => {
    setForm((current) => ({ ...current, ...change }))
    setErrors({})
    setFailure(undefined)
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const made = rmaRequestOf(orderId, line, form, new Date())
    if ('errors' in made) {
      setErrors(made.errors)
      return
    }
    request.mutate(made.body, {
      onSuccess: (rma) => navigate(`/returns/${encodeURIComponent(rma.id)}`),
      onError: (error) => {
        const refusals = error instanceof ApiError ? rmaRefusalsOf(error.status, error.problem) : undefined
        if (refusals) setErrors(refusals)
        else setFailure(error)
      },
    })
  }
  const describedBy = (field: RmaField) => (errors[field] ? `${id}-${field}` : undefined)

  return (
    <form
      className="rma-form"
      aria-label={`Return or claim warranty: ${line.productName ?? line.variantId}`}
      onSubmit={submit}
      noValidate
    >
      <fieldset className="test-cards" aria-describedby={describedBy('kind')}>
        <legend>What would you like to do?</legend>
        {options.map((option) => (
          <label key={option.kind} className="test-card">
            <input
              type="radio"
              name={`${id}-kind`}
              value={option.kind}
              checked={form.kind === option.kind}
              disabled={!option.available}
              onChange={() => update({ kind: option.kind })}
            />
            <span className="test-card-label">{option.label}</span>
            <span className="test-card-outcome muted">
              {option.available && option.endsAt ? `Open until ${formatDay(option.endsAt)}` : option.reason}
            </span>
          </label>
        ))}
      </fieldset>
      <FieldError id={`${id}-kind`} message={errors.kind} />

      {serialTracked ? (
        <fieldset className="rma-units" aria-describedby={describedBy('units')}>
          <legend>Which units?</legend>
          {line.serialNumbers.map((serial) => (
            <label key={serial} className="rma-unit">
              <input
                type="checkbox"
                checked={form.serialNumbers.includes(serial)}
                onChange={(event) =>
                  update({
                    serialNumbers: event.target.checked
                      ? [...form.serialNumbers, serial]
                      : form.serialNumbers.filter((chosen) => chosen !== serial),
                  })
                }
              />
              <code>{serial}</code>
            </label>
          ))}
        </fieldset>
      ) : (
        <label className="field">
          <span>
            How many? <span className="muted">(up to {line.quantity})</span>
          </span>
          <Input
            type="number"
            inputMode="numeric"
            min={1}
            max={line.quantity}
            value={form.quantity}
            onChange={(event) => update({ quantity: event.target.value })}
            aria-invalid={errors.units ? true : undefined}
            aria-describedby={describedBy('units')}
          />
        </label>
      )}
      <FieldError id={`${id}-units`} message={errors.units} />

      <label className="field">
        <span>What’s wrong?</span>
        <textarea
          className="input textarea"
          rows={4}
          value={form.reason}
          onChange={(event) => update({ reason: event.target.value })}
          aria-invalid={errors.reason ? true : undefined}
          aria-describedby={`${id}-reason-count`}
        />
        <span
          id={`${id}-reason-count`}
          className={form.reason.trim().length > maxReason ? 'char-count over' : 'char-count'}
        >
          {form.reason.trim().length.toLocaleString('en')} / {maxReason.toLocaleString('en')}
        </span>
      </label>
      <FieldError id={`${id}-reason`} message={errors.reason} />

      {errors.form && (
        <p className="alert alert-info" role="alert">
          {errors.form}
        </p>
      )}
      {failure !== undefined && <ErrorMessage error={failure} title="We couldn't send your request" />}
      <div className="review-form-actions">
        <Button onClick={onCancel} disabled={request.isPending}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={request.isPending}>
          Send request
        </Button>
      </div>
    </form>
  )
}

function FieldError({ id, message }: { id: string; message: string | undefined }) {
  if (!message) return null
  return (
    <span id={id} className="field-error" role="alert">
      {message}
    </span>
  )
}
