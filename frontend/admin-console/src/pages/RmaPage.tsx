import { type FormEvent, useId, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { useCurrencies } from '../api/currencies'
import { isNotFound } from '../api/failure'
import { fieldErrorsOf } from '../api/fieldErrors'
import { ApiError } from '../api/http'
import { useRmaNotifications } from '../api/notifications'
import { useDecideRma, useRma, useStepRma } from '../api/rmas'
import { useReplacement } from '../api/shipments'
import { NotFound } from '../components/NotFound'
import { Notifications } from '../components/Notifications'
import { RmaStatusBadge } from '../components/RmaStatusBadge'
import { SendBackDeadline } from '../components/SendBackDeadline'
import { ShipmentStatusBadge } from '../components/ShipmentStatusBadge'
import { ErrorMessage, FieldError } from '../components/Status'
import { Button } from '../components/ui/Button'
import { Icon } from '../components/ui/Icon'
import { type Money, formatMoney } from '../domain/money'
import { formatTime, orderReference } from '../domain/order'
import {
  type InspectionOutcome,
  type Received,
  type Rma,
  type ResolutionView,
  decisionOf,
  historyRowsOf,
  inspectionOf,
  isRefusing,
  outcomeLabel,
  outcomesOf,
  receiveOf,
  receiveRefusalsOf,
  receivedChecksOf,
  resolutionOf,
  rmaKindLabel,
  serialFieldOf,
  windowOf,
} from '../domain/rma'
import type { FromQueueState } from './RmaQueuePage'

/**
 * `/rmas/:id`: one RMA, whoever requested it, with what it claims, the window it was checked
 * against, its Refund amount and its RMA history. A requested one can be approved, with an optional
 * note, or rejected, with a reason, once; an approved one's units received at its Warehouse by the
 * Send-back deadline; a received one inspected. Then it shows how the returns Saga resolved it. The
 * Notifications its Customer was sent about it are listed last.
 */
export function RmaPage() {
  const { id } = useParams()
  const rma = useRma(id!)
  const currencies = useCurrencies()

  if (isNotFound(rma.error)) return <NotFound what="RMA" />
  const failed = rma.error
    ? { error: rma.error, title: "Couldn't load the RMA", query: rma }
    : currencies.error
      ? { error: currencies.error, title: "Couldn't load the currencies", query: currencies }
      : undefined
  if (failed) {
    return (
      <>
        <BackLink />
        <ErrorMessage
          error={failed.error}
          title={failed.title}
          retrying={failed.query.isFetching}
          onRetry={() => failed.query.refetch()}
        />
      </>
    )
  }
  if (!rma.data || !currencies.data) {
    return (
      <div aria-busy="true">
        <BackLink />
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  const known = currencies.data
  // As of the RMA's fetch: it moves on with each refetch.
  const now = new Date(rma.dataUpdatedAt)
  return <RmaDetail rma={rma.data} money={(amount) => formatMoney(amount, known)} now={now} />
}

/** Back to the queue, on the tab and page the RMA was opened from. */
function BackLink() {
  const queue = (useLocation().state as FromQueueState | null)?.queue ?? ''
  return (
    <Link to={`/rmas${queue}`} className="back-link">
      <Icon name="arrowLeft" size={16} />
      RMAs
    </Link>
  )
}

function RmaDetail({ rma, money, now }: { rma: Rma; money: (amount: Money) => string; now: Date }) {
  const window = windowOf(rma.window)
  const history = historyRowsOf(rma)
  const resolution = resolutionOf(rma)
  const notifications = useRmaNotifications(rma.id)
  return (
    <>
      <BackLink />
      <div className="page-heading">
        <h1>
          {rmaKindLabel(rma.kind)} <code>{rma.reference}</code>
        </h1>
        <RmaStatusBadge status={rma.status} />
      </div>

      <section className="panel" aria-labelledby="rma-facts">
        <h2 id="rma-facts" className="visually-hidden">
          About this RMA
        </h2>
        <dl className="facts">
          <div>
            <dt>Order</dt>
            <dd>
              <Link to={`/orders/${rma.orderId}`}>
                <code>#{orderReference({ id: rma.orderId })}</code>
              </Link>
            </dd>
          </div>
          <div>
            <dt>Customer ID</dt>
            <dd>
              <code>{rma.customerId}</code>
            </dd>
          </div>
          <div>
            <dt>Product</dt>
            <dd>{rma.productName ?? <span className="muted">Unknown</span>}</dd>
          </div>
          <div>
            <dt>Variant</dt>
            <dd>
              <code>{rma.variantId}</code>
            </dd>
          </div>
          <div>
            <dt>Quantity</dt>
            <dd>{rma.quantity}</dd>
          </div>
          <div>
            <dt>Serial numbers</dt>
            <dd>
              {rma.serialNumbers.length === 0 ? (
                <span className="muted">Not tracked</span>
              ) : (
                rma.serialNumbers.map((serial) => (
                  <code key={serial} className="serial-number">
                    {serial}
                  </code>
                ))
              )}
            </dd>
          </div>
          <div>
            <dt>Kind</dt>
            <dd>{rmaKindLabel(rma.kind)}</dd>
          </div>
          <div>
            <dt>Requested</dt>
            <dd>
              <time dateTime={rma.requestedAt}>{formatTime(rma.requestedAt)}</time>
            </dd>
          </div>
          <div>
            <dt>Refund amount</dt>
            <dd>
              {rma.refundAmount ? money(rma.refundAmount) : <span className="muted">Not known until its Order is</span>}
            </dd>
          </div>
          {rma.sendBackBy && (
            <div>
              <dt>Send-back deadline</dt>
              <dd>
                <SendBackDeadline sendBackBy={rma.sendBackBy} now={now} awaited={rma.status === 'APPROVED'} />
              </dd>
            </div>
          )}
          {rma.warehouseId && (
            <div>
              <dt>Warehouse</dt>
              <dd>
                <code>{rma.warehouseId}</code>
              </dd>
            </div>
          )}
          {rma.inspection && (
            <div>
              <dt>Inspection</dt>
              <dd>
                {outcomeLabel(rma.inspection.outcome)}
                {rma.inspection.note && <small className="hint">{rma.inspection.note}</small>}
              </dd>
            </div>
          )}
        </dl>
        <h3 className="facts-heading">The Customer's reason</h3>
        <blockquote className="customer-reason">{rma.reason}</blockquote>
      </section>

      <section className="panel" aria-labelledby="rma-window">
        <h2 id="rma-window">Checked against</h2>
        <dl className="facts">
          <div>
            <dt>Window</dt>
            <dd>{window.name}</dd>
          </div>
          {window.terms && (
            <div>
              <dt>Warranty terms</dt>
              <dd>{window.terms}</dd>
            </div>
          )}
          <div>
            <dt>From delivery</dt>
            <dd>
              <time dateTime={window.startsAt}>{window.from}</time>
            </dd>
          </div>
          <div>
            <dt>Until</dt>
            <dd>
              <time dateTime={window.endsAt}>{window.to}</time>
            </dd>
          </div>
        </dl>
        <p className="hint">
          As it was when the RMA was requested: later changes to the Warranty terms don't change it.
        </p>
      </section>

      {rma.status === 'REQUESTED' && <Decide rma={rma} />}
      {rma.status === 'APPROVED' && <Receive rma={rma} />}
      {rma.status === 'RECEIVED' && <Inspect rma={rma} />}
      {resolution && <Resolution rma={rma} view={resolution} money={money} />}

      <section className="panel" aria-labelledby="rma-history">
        <h2 id="rma-history">RMA history</h2>
        <div className="table-scroll">
          <table className="table" aria-labelledby="rma-history">
            <thead>
              <tr>
                <th scope="col">Status</th>
                <th scope="col">When</th>
                <th scope="col">By</th>
                <th scope="col">Note</th>
              </tr>
            </thead>
            <tbody>
              {history.map((row, i) => (
                <tr key={i}>
                  <th scope="row">{row.status}</th>
                  <td>
                    <time dateTime={row.at}>{row.time}</time>
                  </td>
                  <td>{row.staff ? <code>{row.by}</code> : row.by}</td>
                  <td>{row.note ?? <span className="muted">—</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel" aria-labelledby="rma-notifications">
        <h2 id="rma-notifications">Notifications</h2>
        <Notifications notifications={notifications} labelledBy="rma-notifications" />
      </section>
    </>
  )
}

/** Field errors less one field's, once it is edited. */
function without(errors: Record<string, string>, field: string): Record<string, string> {
  return Object.fromEntries(Object.entries(errors).filter(([name]) => name !== field))
}

/** The message for a step someone else took first, or none to show the failure's own. */
function movedOn(error: unknown): string | undefined {
  return error instanceof ApiError && error.problem.reason === 'transitionRefused'
    ? 'It was moved on elsewhere first. It shows as it is now.'
    : undefined
}

/**
 * Receive an approved RMA's units: one input per unit for a serial-tracked Variant, each checked as
 * it is typed against the RMA's Serial numbers, or the number of units for any other. Returns &
 * Warranty's refusals show beside the inputs they name.
 */
function Receive({ rma }: { rma: Rma }) {
  const id = useId()
  const step = useStepRma(rma.id)
  const tracked = rma.serialNumbers.length > 0
  const [entered, setEntered] = useState<string[]>(() => Array<string>(rma.quantity).fill(''))
  const [quantity, setQuantity] = useState('')
  // Refusals from the last Receive, the form's or the server's, each until its input is edited.
  const [errors, setErrors] = useState<Record<string, string>>({})

  const received: Received = tracked ? { serialNumbers: entered } : { quantity }
  const checks = receivedChecksOf(rma.serialNumbers, entered)
  const messageOf = (field: string, unit?: number) => {
    const check = unit === undefined ? undefined : checks[unit]
    return check?.state === 'invalid' ? check.message : errors[field]
  }
  const edited = (field: string) => setErrors((current) => without(current, field))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const request = receiveOf(rma, received)
    if ('errors' in request) {
      setErrors(request.errors)
      return
    }
    setErrors({})
    step.mutate(
      { step: 'receive', body: request.body },
      { onError: (error) => setErrors(receiveErrorsOf(error, received)) },
    )
  }

  const errorId = (field: string) => `${id}-${field.replace(/[^\w-]/g, '-')}-error`
  const shownWhole = step.error && Object.keys(receiveErrorsOf(step.error, received)).length === 0

  return (
    <section className="panel" aria-labelledby={`${id}-heading`}>
      <h2 id={`${id}-heading`}>Receive</h2>
      {step.error && shownWhole && (
        <ErrorMessage error={step.error} title="Couldn't receive it" message={movedOn(step.error)} />
      )}
      <p className="muted">
        {tracked
          ? 'Type or scan the Serial number of each unit that arrived. Each must be one of this RMA’s.'
          : 'Confirm how many units arrived: all of this RMA’s are received at once.'}
      </p>
      <form onSubmit={submit} aria-label="Receive" noValidate>
        {tracked ? (
          <fieldset className="pack-line">
            <legend>
              {rma.productName ?? 'Units'} <code>{rma.variantId}</code>
            </legend>
            {entered.map((typed, unit) => {
              const field = serialFieldOf(unit)
              const message = messageOf(field, unit)
              return (
                <div key={unit} className="field">
                  <label htmlFor={`${id}-${field}`}>
                    {rma.quantity === 1 ? 'Serial number' : `Serial number ${unit + 1} of ${rma.quantity}`}
                  </label>
                  <input
                    id={`${id}-${field}`}
                    className="input"
                    name={field}
                    autoComplete="off"
                    spellCheck={false}
                    value={typed}
                    onChange={(e) => {
                      setEntered(entered.map((value, i) => (i === unit ? e.target.value : value)))
                      edited(field)
                    }}
                    aria-invalid={message ? true : undefined}
                    aria-describedby={message ? errorId(field) : undefined}
                  />
                  <FieldError id={errorId(field)} message={message} />
                </div>
              )
            })}
            <FieldError id={errorId('serialNumbers')} message={errors.serialNumbers} />
          </fieldset>
        ) : (
          <div className="field">
            <label htmlFor={`${id}-quantity`}>Units received</label>
            <input
              id={`${id}-quantity`}
              className="input"
              name="quantity"
              inputMode="numeric"
              autoComplete="off"
              value={quantity}
              onChange={(e) => {
                setQuantity(e.target.value)
                edited('quantity')
              }}
              aria-invalid={errors.quantity ? true : undefined}
              aria-describedby={errors.quantity ? errorId('quantity') : `${id}-quantity-hint`}
            />
            <span id={`${id}-quantity-hint`} className="hint">
              This RMA is for {rma.quantity === 1 ? '1 unit' : `${rma.quantity} units`}.
            </span>
            <FieldError id={errorId('quantity')} message={errors.quantity} />
          </div>
        )}
        <div className="form-actions">
          <Button type="submit" variant="primary" loading={step.isPending}>
            <Icon name="check" size={16} />
            Receive
          </Button>
        </div>
      </form>
    </section>
  )
}

/** Returns & Warranty's refusal of a receipt by the inputs it names: a 422's units, or a 400's fields. */
function receiveErrorsOf(error: unknown, received: Received): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  const refusals = receiveRefusalsOf(error.problem, received)
  return Object.keys(refusals).length > 0 ? refusals : fieldErrorsOf(error)
}

/** Inspect a received RMA: an outcome for its kind, and a note, which a refusing outcome needs. */
function Inspect({ rma }: { rma: Rma }) {
  const id = useId()
  const step = useStepRma(rma.id)
  const options = outcomesOf(rma.kind)
  const [outcome, setOutcome] = useState<InspectionOutcome>()
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const refusing = isRefusing(outcome)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const request = inspectionOf(outcome, note)
    if ('errors' in request) {
      setErrors(request.errors)
      return
    }
    setErrors({})
    step.mutate({ step: 'inspect', body: request.body }, { onError: (error) => setErrors(fieldErrorsOf(error)) })
  }

  const errorId = (field: string) => `${id}-${field}-error`
  const shownWhole = step.error && Object.keys(fieldErrorsOf(step.error)).length === 0

  return (
    <section className="panel" aria-labelledby={`${id}-heading`}>
      <h2 id={`${id}-heading`}>Inspect</h2>
      {step.error && shownWhole && (
        <ErrorMessage error={step.error} title="Couldn't record the Inspection" message={movedOn(step.error)} />
      )}
      <form onSubmit={submit} aria-label="Inspect" noValidate>
        <fieldset
          className="check-list"
          aria-invalid={errors.outcome ? true : undefined}
          aria-describedby={errors.outcome ? errorId('outcome') : undefined}
        >
          <legend>Outcome</legend>
          {options.map((option) => (
            <label key={option.outcome} className="check">
              <input
                type="radio"
                name="outcome"
                value={option.outcome}
                checked={outcome === option.outcome}
                onChange={() => {
                  setOutcome(option.outcome)
                  setErrors((current) => without(current, 'outcome'))
                }}
              />
              {option.label}
            </label>
          ))}
        </fieldset>
        <FieldError id={errorId('outcome')} message={errors.outcome} />
        <label className="field" htmlFor={`${id}-note`}>
          <span>Note {!refusing && <span className="hint">(optional)</span>}</span>
        </label>
        <textarea
          id={`${id}-note`}
          className="input"
          rows={3}
          value={note}
          onChange={(e) => {
            setNote(e.target.value)
            setErrors((current) => without(current, 'note'))
          }}
          aria-invalid={errors.note ? true : undefined}
          aria-describedby={errors.note ? errorId('note') : undefined}
        />
        {refusing && <span className="hint">Say what was found: nothing is given back.</span>}
        <FieldError id={errorId('note')} message={errors.note} />
        <div className="form-actions">
          <Button type="submit" variant="primary" loading={step.isPending}>
            <Icon name="check" size={16} />
            Record the Inspection
          </Button>
        </div>
      </form>
    </section>
  )
}

/**
 * How the returns Saga resolved the RMA after its Inspection: the Refund's amount and its reference
 * at the payment gateway, the replacement Shipment's status with a link, the Inspection's note for
 * one closed, or the hold's reason. The page asks again while the Saga is at work.
 */
function Resolution({ rma, view, money }: { rma: Rma; view: ResolutionView; money: (amount: Money) => string }) {
  return (
    <section className="panel" aria-labelledby="rma-resolution">
      <h2 id="rma-resolution">Resolution</h2>
      {view.kind === 'resolving' && <p className="muted">The returns Saga is resolving it…</p>}
      {view.kind === 'replacement' && <Replacement rma={rma} />}
      {view.kind === 'refund' && (
        <dl className="facts">
          <div>
            <dt>Refunded</dt>
            <dd>{view.amount ? money(view.amount) : <span className="muted">Unknown</span>}</dd>
          </div>
          <div>
            <dt>Gateway reference</dt>
            <dd>
              <code>{view.reference ?? '—'}</code>
            </dd>
          </div>
        </dl>
      )}
      {view.kind === 'closed' && (
        <p>
          Closed: nothing was given back.
          {view.note && <small className="hint">{view.note}</small>}
        </p>
      )}
      {view.kind === 'hold' && (
        <div className="alert alert-danger" role="alert">
          <Icon name="alert" />
          <div className="alert-body">
            <strong>On hold: the Refund was declined</strong>
            <span>{view.reason ?? 'No reason was given.'}</span>
          </div>
        </div>
      )}
    </section>
  )
}

/** The replacement Shipment the returns Saga created for a confirmed fault; none when it refunded instead. */
function Replacement({ rma }: { rma: Rma }) {
  const replacement = useReplacement(rma.id)
  if (replacement.error) {
    return (
      <ErrorMessage
        error={replacement.error}
        title="Couldn't load the replacement"
        retrying={replacement.isFetching}
        onRetry={() => replacement.refetch()}
      />
    )
  }
  if (replacement.data === undefined) {
    return (
      <div aria-busy="true">
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  if (replacement.data === null) return <p className="muted">The returns Saga is resolving it…</p>
  const { id, status, warehouseId, trackingNumber } = replacement.data
  return (
    <dl className="facts">
      <div>
        <dt>Replacement</dt>
        <dd>
          <ShipmentStatusBadge status={status} />
        </dd>
      </div>
      <div>
        <dt>Warehouse</dt>
        <dd>
          <code>{warehouseId}</code>
        </dd>
      </div>
      {trackingNumber && (
        <div>
          <dt>Tracking number</dt>
          <dd>
            <code>{trackingNumber}</code>
          </dd>
        </div>
      )}
      <div>
        <dt>Shipment</dt>
        <dd>
          <Link to={`/fulfillment/${id}`}>Open the replacement</Link>
        </dd>
      </div>
    </dl>
  )
}

/** Approve, with an optional note, or reject, with a reason the Customer is told. Only once. */
function Decide({ rma }: { rma: Rma }) {
  const id = useId()
  const decide = useDecideRma(rma.id)
  const [note, setNote] = useState('')
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState<{ approve?: string; reject?: string }>({})

  const submit = (decision: 'approve' | 'reject') => (event: FormEvent) => {
    event.preventDefault()
    const checked = decisionOf(decision, decision === 'approve' ? note : reason)
    if ('error' in checked) {
      setErrors({ [decision]: checked.error })
      return
    }
    setErrors({})
    decide.mutate({ decision, body: checked.body })
  }

  const alreadyDecided = decide.error instanceof ApiError && decide.error.problem.reason === 'rmaAlreadyDecided'
  const field = (decision: 'approve' | 'reject') => ({
    id: `${id}-${decision}`,
    'aria-invalid': errors[decision] ? true : undefined,
    'aria-describedby': errors[decision] ? `${id}-${decision}-error` : undefined,
  })

  return (
    <section className="panel" aria-labelledby={`${id}-heading`}>
      <h2 id={`${id}-heading`}>Decide</h2>
      {decide.error && (
        <ErrorMessage
          error={decide.error}
          title="Couldn't decide the RMA"
          message={alreadyDecided ? 'Another Staff member decided it first. It shows as they left it.' : undefined}
        />
      )}
      <div className="decide">
        <form onSubmit={submit('approve')} aria-label="Approve">
          <label className="field" htmlFor={`${id}-approve`}>
            <span>
              Note <span className="hint">(optional)</span>
            </span>
          </label>
          <textarea
            className="input"
            rows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            {...field('approve')}
          />
          <FieldError id={`${id}-approve-error`} message={errors.approve} />
          <div className="form-actions">
            <Button
              type="submit"
              variant="primary"
              loading={decide.isPending && decide.variables?.decision === 'approve'}
              disabled={decide.isPending}
            >
              <Icon name="check" size={16} />
              Approve
            </Button>
          </div>
        </form>
        <form onSubmit={submit('reject')} aria-label="Reject">
          <label className="field" htmlFor={`${id}-reject`}>
            Reason
          </label>
          <textarea
            className="input"
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            {...field('reject')}
          />
          <span className="hint">The Customer is told why.</span>
          <FieldError id={`${id}-reject-error`} message={errors.reject} />
          <div className="form-actions">
            <Button
              type="submit"
              variant="danger"
              loading={decide.isPending && decide.variables?.decision === 'reject'}
              disabled={decide.isPending}
            >
              <Icon name="close" size={16} />
              Reject
            </Button>
          </div>
        </form>
      </div>
    </section>
  )
}
