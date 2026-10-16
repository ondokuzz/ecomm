import { type FormEvent, useId, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { isNotFound } from '../api/failure'
import { ApiError } from '../api/http'
import { useDecideRma, useRma } from '../api/rmas'
import { NotFound } from '../components/NotFound'
import { RmaStatusBadge } from '../components/RmaStatusBadge'
import { ErrorMessage, FieldError } from '../components/Status'
import { Button } from '../components/ui/Button'
import { Icon } from '../components/ui/Icon'
import { formatTime, orderReference } from '../domain/order'
import { type Rma, decisionOf, decisionRowsOf, rmaKindLabel, windowOf } from '../domain/rma'
import type { FromQueueState } from './RmaQueuePage'

/**
 * `/rmas/:id`: one RMA, whoever requested it, with what it claims, the window it was checked
 * against and its decision history. A requested one can be approved, with an optional note, or
 * rejected, with a reason, once.
 */
export function RmaPage() {
  const { id } = useParams()
  const rma = useRma(id!)

  if (isNotFound(rma.error)) return <NotFound what="RMA" />
  if (rma.error) {
    return (
      <>
        <BackLink />
        <ErrorMessage
          error={rma.error}
          title="Couldn't load the RMA"
          retrying={rma.isFetching}
          onRetry={() => rma.refetch()}
        />
      </>
    )
  }
  if (!rma.data) {
    return (
      <div aria-busy="true">
        <BackLink />
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  return <RmaDetail rma={rma.data} />
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

function RmaDetail({ rma }: { rma: Rma }) {
  const window = windowOf(rma.window)
  const decisions = decisionRowsOf(rma)
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

      <section className="panel" aria-labelledby="rma-decisions">
        <h2 id="rma-decisions">Decision history</h2>
        {decisions.length === 0 ? (
          <p className="muted">Not decided yet.</p>
        ) : (
          <div className="table-scroll">
            <table className="table" aria-labelledby="rma-decisions">
              <thead>
                <tr>
                  <th scope="col">Decision</th>
                  <th scope="col">When</th>
                  <th scope="col">Decided by</th>
                  <th scope="col">Note</th>
                </tr>
              </thead>
              <tbody>
                {decisions.map((row, i) => (
                  <tr key={i}>
                    <th scope="row">{row.status}</th>
                    <td>
                      <time dateTime={row.at}>{row.time}</time>
                    </td>
                    <td>
                      <code>{row.decidedBy}</code>
                    </td>
                    <td>{row.note ?? <span className="muted">—</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
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
