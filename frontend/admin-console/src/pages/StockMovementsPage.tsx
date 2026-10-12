import { Link, useParams, useSearchParams } from 'react-router'
import { isNotFound } from '../api/failure'
import { useStockMovements } from '../api/products'
import { NotFound } from '../components/NotFound'
import { ErrorMessage, SkeletonRows } from '../components/Status'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Icon } from '../components/ui/Icon'
import { pageCount } from '../domain/order'
import { balanceOf, movementRowsOf, movementsPageSize } from '../domain/stock'

const columns = 6

/**
 * `/products/:sku/stock/:variantId`: a Variant's Stock movements, newest first, a page at a time
 * (`?page=`), and whether its On-hand equals their sum. Read only: Staff set On-hand in the
 * Product editor.
 */
export function StockMovementsPage() {
  const { sku, variantId } = useParams()
  const [params, setParams] = useSearchParams()
  const pageNumber = Math.max(0, Number.parseInt(params.get('page') ?? '', 10) || 0)
  const movements = useStockMovements(variantId!, pageNumber, movementsPageSize)

  if (isNotFound(movements.error)) return <NotFound what="Stock for this Variant" />
  const page = movements.data
  const pages = page ? pageCount(page) : 1
  const show = (next: number) => setParams(next === 0 ? {} : { page: String(next) })
  const balance = page && balanceOf(page)

  return (
    <>
      <Link to={`/products/${encodeURIComponent(sku!)}`} className="back-link">
        <Icon name="arrowLeft" size={16} />
        Product <code>{sku}</code>
      </Link>
      <div className="page-heading">
        <h1>
          Stock movements of <code>{variantId}</code>
        </h1>
        {balance && (
          <Badge tone={page.balanced ? 'success' : 'danger'} dot>
            {balance.label}
          </Badge>
        )}
      </div>
      <p className="muted page-intro">
        Every change to the Variant's On-hand units, or to what Reservations hold of them, newest first. Movements are
        never changed; set On-hand in the Product editor.
      </p>

      {movements.error ? (
        <ErrorMessage
          error={movements.error}
          title="Couldn't load the Stock movements"
          retrying={movements.isFetching}
          onRetry={() => movements.refetch()}
        />
      ) : (
        <>
          {page && balance && (
            <section className="panel" aria-labelledby="stock-balance">
              <h2 id="stock-balance" className="visually-hidden">
                Balance
              </h2>
              <dl className="facts">
                <div>
                  <dt>On-hand</dt>
                  <dd>{page.onHand}</dd>
                </div>
                <div>
                  <dt>On-hand from movements</dt>
                  <dd>{page.onHandFromMovements}</dd>
                </div>
                <div>
                  <dt>Balance</dt>
                  <dd>
                    {balance.label}
                    <small className="hint">{balance.detail}</small>
                  </dd>
                </div>
              </dl>
            </section>
          )}

          <div className="table-scroll">
            <table className="table" aria-label="Stock movements" aria-busy={movements.isPlaceholderData || undefined}>
              <thead>
                <tr>
                  <th scope="col">Kind</th>
                  <th scope="col" className="num">
                    On-hand change
                  </th>
                  <th scope="col" className="num">
                    Reserved change
                  </th>
                  <th scope="col">Reservation</th>
                  <th scope="col">Reason</th>
                  <th scope="col">When</th>
                </tr>
              </thead>
              {page ? (
                <tbody>
                  {page.items.length === 0 && (
                    <tr>
                      <td colSpan={columns} className="table-empty">
                        {page.total === 0 ? 'No Stock movements yet.' : 'There are no Stock movements on this page.'}
                      </td>
                    </tr>
                  )}
                  {movementRowsOf(page.items).map((row, i) => (
                    <tr key={i}>
                      <th scope="row">{row.kind}</th>
                      <td className="num">{row.onHandChange}</td>
                      <td className="num">{row.reservedChange}</td>
                      <td>
                        <code>{row.reservation}</code>
                      </td>
                      <td>{row.reason}</td>
                      <td>
                        <time dateTime={row.at}>{row.time}</time>
                      </td>
                    </tr>
                  ))}
                </tbody>
              ) : (
                <SkeletonRows rows={5} columns={columns} />
              )}
            </table>
          </div>

          {page && (
            <nav className="pager" aria-label="Pages">
              <span className="muted">
                Page {page.page + 1} of {pages} · {page.total === 1 ? '1 movement' : `${page.total} movements`}
              </span>
              <Button
                size="sm"
                disabled={page.page === 0 || movements.isPlaceholderData}
                onClick={() => show(page.page - 1)}
              >
                Previous
              </Button>
              <Button
                size="sm"
                disabled={page.page + 1 >= pages || movements.isPlaceholderData}
                onClick={() => show(page.page + 1)}
              >
                Next
              </Button>
            </nav>
          )}
        </>
      )}
    </>
  )
}
