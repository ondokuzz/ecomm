import { Link, useSearchParams } from 'react-router'
import { useFormatMoney } from '../api/currencies'
import { useRmas } from '../api/rmas'
import { OrdersSkeleton } from '../components/PageSkeletons'
import { RmaStatusBadge } from '../components/RmaStatusBadge'
import { EmptyState, ErrorState } from '../components/Status'
import { ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { type Page, pageCount, pageIndexFromUrl } from '../domain/paging'
import { type Rma, formatDay, rmaKindLabel } from '../domain/rma'

/**
 * The Customer's RMAs as cards, newest first, a page at a time, each with its RMA reference, Product,
 * kind, status and when it was requested; the page is in the URL, counting from 1.
 */
export function ReturnsPage() {
  const [params] = useSearchParams()
  const rmas = useRmas(pageIndexFromUrl(params.get('page')))

  if (rmas.isPending) return <OrdersSkeleton />
  if (rmas.error) {
    return (
      <ErrorState
        title="We couldn't load your returns"
        error={rmas.error}
        retrying={rmas.isFetching}
        onRetry={() => rmas.refetch()}
      />
    )
  }

  return (
    <section>
      <h1>My returns</h1>
      {rmas.data.total === 0 ? (
        <EmptyState
          title="No returns or warranty claims"
          action={
            <ButtonLink variant="primary" size="lg" to="/orders">
              My orders
            </ButtonLink>
          }
        >
          To return a product or claim its warranty, open its delivered order.
        </EmptyState>
      ) : (
        <>
          {rmas.data.items.length === 0 ? (
            <p className="muted">
              There are no returns on this page. <Link to="/returns">Back to your newest returns</Link>
            </p>
          ) : (
            <ul className="order-cards" aria-label="Returns">
              {rmas.data.items.map((rma) => (
                <RmaCard key={rma.id} rma={rma} />
              ))}
            </ul>
          )}
          <Pager page={rmas.data} />
        </>
      )}
    </section>
  )
}

function RmaCard({ rma }: { rma: Rma }) {
  const formatMoney = useFormatMoney()
  return (
    <li>
      <Link to={`/returns/${encodeURIComponent(rma.id)}`} className="card-link">
        <Card interactive className="order-card">
          <div className="order-card-top">
            <div>
              <span className="order-card-reference">{rma.reference}</span>
              <span className="muted order-card-date">{formatDay(rma.requestedAt)}</span>
            </div>
            <RmaStatusBadge status={rma.status} />
          </div>
          <span>{rma.productName ?? rma.variantId}</span>
          <div className="order-card-bottom">
            <span className="muted">
              {rmaKindLabel(rma.kind)}, {rma.quantity === 1 ? '1 unit' : `${rma.quantity} units`}
            </span>
            {rma.kind === 'RETURN' && rma.refundAmount && (
              <span className="price">{formatMoney(rma.refundAmount)}</span>
            )}
          </div>
        </Card>
      </Link>
    </li>
  )
}

/** Newer and older pages of RMAs; hidden while everything fits on one page. */
function Pager({ page }: { page: Page<Rma> }) {
  const count = pageCount(page)
  if (count === 1) return null
  const shown = page.page + 1
  return (
    <nav className="pager" aria-label="Return pages">
      {shown > 1 ? (
        <ButtonLink to={shown === 2 ? '/returns' : `/returns?page=${shown - 1}`} rel="prev">
          Newer returns
        </ButtonLink>
      ) : (
        <span />
      )}
      <span className="muted">
        Page {shown} of {count}
      </span>
      {shown < count ? (
        <ButtonLink to={`/returns?page=${shown + 1}`} rel="next">
          Older returns
        </ButtonLink>
      ) : (
        <span />
      )}
    </nav>
  )
}
