import { type FormEvent, useId, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { isNotFound } from '../api/failure'
import { fieldErrorsOf } from '../api/fieldErrors'
import { ApiError } from '../api/http'
import { useMoveShipment, useShipment } from '../api/shipments'
import { NotFound } from '../components/NotFound'
import { ShipmentStatusBadge } from '../components/ShipmentStatusBadge'
import { ErrorMessage, FieldError } from '../components/Status'
import { Button } from '../components/ui/Button'
import { Icon } from '../components/ui/Icon'
import { formatTime, orderReference } from '../domain/order'
import {
  type Shipment,
  type ShipmentLine,
  packRequestOf,
  replacementOf,
  serialCheckOf,
  serialFieldOf,
  shipmentHistoryRowsOf,
  trackingStatusLabel,
} from '../domain/shipment'
import type { FromQueueState } from './FulfillmentQueuePage'

/**
 * `/fulfillment/:id`: one Shipment, with where it goes, what to pick and its status history. Staff
 * pick it, then pack it with one Serial number per unit of each serial-tracked line; the fulfillment
 * Saga takes it from there, to the Carrier or on hold. A replacement shows its RMA instead of the
 * Order's Payment, and the returns Saga takes it to the Carrier.
 */
export function ShipmentPage() {
  const { id } = useParams()
  const shipment = useShipment(id!)

  if (isNotFound(shipment.error)) return <NotFound what="Shipment" />
  if (shipment.error && !shipment.data) {
    return (
      <>
        <BackLink />
        <ErrorMessage
          error={shipment.error}
          title="Couldn't load the Shipment"
          retrying={shipment.isFetching}
          onRetry={() => shipment.refetch()}
        />
      </>
    )
  }
  if (!shipment.data) {
    return (
      <div aria-busy="true">
        <BackLink />
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  return <ShipmentDetail shipment={shipment.data} />
}

/** Back to the queue, on the tab and page the Shipment was opened from. */
function BackLink() {
  const queue = (useLocation().state as FromQueueState | null)?.queue ?? ''
  return (
    <Link to={`/fulfillment${queue}`} className="back-link">
      <Icon name="arrowLeft" size={16} />
      Fulfillment
    </Link>
  )
}

function ShipmentDetail({ shipment }: { shipment: Shipment }) {
  const address = shipment.shippingAddress
  const option = shipment.shippingOption
  const history = shipmentHistoryRowsOf(shipment)
  const replacement = replacementOf(shipment)
  return (
    <>
      <BackLink />
      <div className="page-heading">
        <h1>
          {replacement ? (
            <>
              Replacement for <code>{replacement.rmaReference}</code>
            </>
          ) : (
            <>
              Shipment for Order <code>#{orderReference({ id: shipment.orderId })}</code>
            </>
          )}
        </h1>
        <ShipmentStatusBadge status={shipment.status} />
      </div>

      {shipment.status === 'ON_HOLD' && (
        <div className="alert alert-danger" role="alert">
          <Icon name="alert" />
          <div className="alert-body">
            <strong>On hold: the Payment couldn't be captured</strong>
            <span>{shipment.holdReason ?? 'The payment gateway gave no reason.'}</span>
            <small className="hint">It stays out of the Carrier's hands.</small>
          </div>
        </div>
      )}

      <section className="panel" aria-labelledby="shipment-facts">
        <h2 id="shipment-facts" className="visually-hidden">
          About this Shipment
        </h2>
        <dl className="facts">
          {replacement && (
            <div>
              <dt>RMA</dt>
              <dd>
                <Link to={`/rmas/${replacement.rmaId}`}>
                  <code>{replacement.rmaReference}</code>
                </Link>
                <small className="hint">A replacement: no Payment is captured for it.</small>
              </dd>
            </div>
          )}
          <div>
            <dt>Order</dt>
            <dd>
              <Link to={`/orders/${shipment.orderId}`}>
                <code>#{orderReference({ id: shipment.orderId })}</code>
              </Link>
            </dd>
          </div>
          <div>
            <dt>Warehouse</dt>
            <dd>
              <code>{shipment.warehouseId}</code>
            </dd>
          </div>
          <div>
            <dt>Shipping option</dt>
            <dd>
              {option.id.charAt(0) + option.id.slice(1).toLowerCase()}, {option.carrier}
              {option.estimatedDays != null && (
                <small className="hint">
                  {option.estimatedDays === 1 ? '1 working day' : `${option.estimatedDays} working days`}
                </small>
              )}
            </dd>
          </div>
          <div>
            <dt>{replacement ? 'Created' : 'Paid'}</dt>
            <dd>
              <time dateTime={shipment.createdAt}>{formatTime(shipment.createdAt)}</time>
            </dd>
          </div>
          <div>
            <dt>Customer ID</dt>
            <dd>
              <code>{shipment.customerId}</code>
            </dd>
          </div>
        </dl>
      </section>

      <section className="panel" aria-labelledby="shipment-address">
        <h2 id="shipment-address">Shipping address</h2>
        <address className="shipping-address">
          {[
            address.recipientName,
            address.line1,
            address.line2,
            `${address.postcode} ${address.city}`,
            address.region,
            address.country,
            address.phone,
          ]
            .filter(Boolean)
            .map((part, i) => (
              <span key={i}>{part}</span>
            ))}
        </address>
      </section>

      <section className="panel" aria-labelledby="shipment-lines">
        <h2 id="shipment-lines">Lines</h2>
        <div className="table-scroll">
          <table className="table" aria-labelledby="shipment-lines">
            <thead>
              <tr>
                <th scope="col">Product</th>
                <th scope="col">Variant</th>
                <th scope="col" className="num">
                  Quantity
                </th>
                <th scope="col">Serial numbers</th>
              </tr>
            </thead>
            <tbody>
              {shipment.lines.map((line) => (
                <tr key={line.variantId}>
                  <th scope="row">{line.productName}</th>
                  <td>
                    <code>{line.variantId}</code>
                  </td>
                  <td className="num">{line.quantity}</td>
                  <td>
                    {line.serialNumbers.length > 0 ? (
                      line.serialNumbers.map((serial) => (
                        <code key={serial} className="serial-number">
                          {serial}
                        </code>
                      ))
                    ) : (
                      <span className="muted">{trackedAs(line)}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {shipment.status === 'AWAITING_PICK' && <Pick shipment={shipment} />}
      {shipment.status === 'PICKED' && <Pack shipment={shipment} />}
      {shipment.status === 'PACKED' && (
        <p className="alert alert-info" role="status">
          {replacement
            ? 'Packed. The returns Saga hands it to the Carrier by itself.'
            : 'Packed. It goes to the Carrier by itself once its Payment is captured.'}
        </p>
      )}
      {shipment.trackingNumber && <Tracking shipment={shipment} />}

      <section className="panel" aria-labelledby="shipment-history">
        <h2 id="shipment-history">Status history</h2>
        <div className="table-scroll">
          <table className="table" aria-labelledby="shipment-history">
            <thead>
              <tr>
                <th scope="col">Status</th>
                <th scope="col">When</th>
                <th scope="col">Changed by</th>
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
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}

/** What a line without Serial numbers yet will take: none, or one per unit when packed. */
function trackedAs(line: ShipmentLine): string {
  if (line.serialTracking === 'NONE') return 'Not tracked'
  return line.serialTracking === 'IMEI' ? 'An IMEI per unit, when packed' : 'A serial number per unit, when packed'
}

/** Marks the Shipment picked: it is in hand. */
function Pick({ shipment }: { shipment: Shipment }) {
  const move = useMoveShipment(shipment.id)
  return (
    <section className="panel" aria-labelledby="shipment-pick">
      <h2 id="shipment-pick">Pick</h2>
      {move.error && (
        <ErrorMessage error={move.error} title="Couldn't mark it picked" message={movedAway(move.error)} />
      )}
      <p className="muted">Collect each line from the shelves, then mark it picked so the team knows it's in hand.</p>
      <div className="form-actions">
        <Button variant="primary" loading={move.isPending} onClick={() => move.mutate({ step: 'pick' })}>
          <Icon name="check" size={16} />
          Mark picked
        </Button>
      </div>
    </section>
  )
}

/**
 * Packs the Shipment with one Serial number per unit of each serial-tracked line, each checked as
 * typed. Fulfillment & Shipping's violations show against the same inputs; one already shipped on
 * another Shipment shows against the input that holds it.
 */
function Pack({ shipment }: { shipment: Shipment }) {
  const id = useId()
  const move = useMoveShipment(shipment.id)
  const tracked = shipment.lines.filter((line) => line.serialTracking !== 'NONE')
  const [entered, setEntered] = useState<Record<string, string[]>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})

  const type = (line: ShipmentLine, unit: number, value: string) => {
    const units = [...(entered[line.variantId] ?? Array<string>(line.quantity).fill(''))]
    units[unit] = value
    setEntered({ ...entered, [line.variantId]: units })
    const field = serialFieldOf(line.variantId, unit)
    const check = serialCheckOf(line.serialTracking as 'IMEI' | 'SERIAL', value)
    const rest = Object.fromEntries(
      Object.entries(errors).filter(([name]) => name !== field && name !== `serialNumbers.${line.variantId}`),
    )
    setErrors(check.state === 'invalid' ? { ...rest, [field]: check.message } : rest)
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const request = packRequestOf(shipment.lines, entered)
    if ('errors' in request) {
      setErrors(request.errors)
      return
    }
    setErrors({})
    move.mutate(
      { step: 'pack', body: request.body },
      { onError: (error) => setErrors(serverErrorsOf(error, request.body.serialNumbers)) },
    )
  }

  const shownWhole = move.error && Object.keys(serverErrorsOf(move.error, {})).length === 0
  const errorId = (field: string) => `${id}-${field.replace(/[^\w-]/g, '-')}-error`

  return (
    <section className="panel" aria-labelledby="shipment-pack">
      <h2 id="shipment-pack">Pack</h2>
      {move.error && (shownWhole || isAlreadyShipped(move.error)) && (
        <ErrorMessage error={move.error} title="Couldn't pack it" message={movedAway(move.error)} />
      )}
      <form onSubmit={submit} aria-label="Pack" noValidate>
        {tracked.length === 0 && <p className="muted">Nothing on it is serial-tracked: pack it as it is.</p>}
        {tracked.map((line) => {
          const kind = line.serialTracking === 'IMEI' ? 'IMEI' : 'Serial number'
          const lineField = `serialNumbers.${line.variantId}`
          return (
            <fieldset key={line.variantId} className="pack-line">
              <legend>
                {line.productName} <code>{line.variantId}</code>
              </legend>
              {Array.from({ length: line.quantity }, (_, unit) => {
                const field = serialFieldOf(line.variantId, unit)
                return (
                  // The error hangs outside the label, so it never becomes part of the input's name.
                  <div key={unit} className="field">
                    <label htmlFor={`${id}-${field}`}>
                      {line.quantity === 1 ? kind : `${kind} ${unit + 1} of ${line.quantity}`}
                    </label>
                    <input
                      id={`${id}-${field}`}
                      className="input"
                      name={field}
                      autoComplete="off"
                      spellCheck={false}
                      inputMode={line.serialTracking === 'IMEI' ? 'numeric' : undefined}
                      value={entered[line.variantId]?.[unit] ?? ''}
                      onChange={(e) => type(line, unit, e.target.value)}
                      aria-invalid={errors[field] ? true : undefined}
                      aria-describedby={errors[field] ? errorId(field) : undefined}
                    />
                    <FieldError id={errorId(field)} message={errors[field]} />
                  </div>
                )
              })}
              <FieldError id={errorId(lineField)} message={errors[lineField]} />
            </fieldset>
          )
        })}
        <div className="form-actions">
          <Button type="submit" variant="primary" loading={move.isPending}>
            <Icon name="check" size={16} />
            Pack
          </Button>
        </div>
      </form>
    </section>
  )
}

/**
 * The server's refusal by field: a 422's violations, named as the inputs are, or a 409
 * `serialAlreadyShipped` against the input holding that Serial number.
 */
function serverErrorsOf(error: unknown, sent: Record<string, string[]>): Record<string, string> {
  if (isAlreadyShipped(error)) {
    const serial = (error as ApiError).problem.serialNumber
    for (const [variantId, serials] of Object.entries(sent)) {
      const unit = serials.indexOf(serial as string)
      if (unit >= 0) return { [serialFieldOf(variantId, unit)]: 'Already shipped on another Shipment.' }
    }
    return {}
  }
  return fieldErrorsOf(error)
}

function isAlreadyShipped(error: unknown): boolean {
  return error instanceof ApiError && error.problem.reason === 'serialAlreadyShipped'
}

/** The message for a step someone else took first, or none to show the failure's own. */
function movedAway(error: unknown): string | undefined {
  return error instanceof ApiError && error.problem.reason === 'illegalTransition'
    ? 'It was moved on elsewhere first. It shows as it is now.'
    : undefined
}

/** The Carrier, its tracking number, and its Tracking events, once the fulfillment Saga dispatched it. */
function Tracking({ shipment }: { shipment: Shipment }) {
  return (
    <section className="panel" aria-labelledby="shipment-tracking">
      <h2 id="shipment-tracking">Tracking</h2>
      <dl className="facts">
        <div>
          <dt>Carrier</dt>
          <dd>{shipment.carrier}</dd>
        </div>
        <div>
          <dt>Tracking number</dt>
          <dd>
            <code>{shipment.trackingNumber}</code>
          </dd>
        </div>
        {shipment.deliveredAt && (
          <div>
            <dt>Delivered</dt>
            <dd>
              <time dateTime={shipment.deliveredAt}>{formatTime(shipment.deliveredAt)}</time>
            </dd>
          </div>
        )}
      </dl>
      {shipment.trackingEvents.length === 0 ? (
        <p className="muted">No Tracking events from the Carrier yet.</p>
      ) : (
        <div className="table-scroll">
          <table className="table" aria-label="Tracking events">
            <thead>
              <tr>
                <th scope="col">Event</th>
                <th scope="col">When</th>
              </tr>
            </thead>
            <tbody>
              {[...shipment.trackingEvents].reverse().map((event) => (
                <tr key={event.eventId}>
                  <th scope="row">{trackingStatusLabel(event.status)}</th>
                  <td>
                    <time dateTime={event.occurredAt}>{formatTime(event.occurredAt)}</time>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
