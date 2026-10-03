import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useCoupons, useDeleteCoupon, useSwitchCoupon } from '../api/coupons'
import { useCurrencies } from '../api/currencies'
import { PromotionsNav } from '../components/PromotionsNav'
import { ErrorMessage, SkeletonRows } from '../components/Status'
import { Badge } from '../components/ui/Badge'
import { Button, ButtonLink } from '../components/ui/Button'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { Icon } from '../components/ui/Icon'
import type { Coupon } from '../domain/coupon'
import { formatMoney } from '../domain/money'
import { describeDiscount, describeWindow } from '../domain/promotion'
import type { SavedState } from './CategoryListPage'

const columns = 6

export function CouponListPage() {
  const coupons = useCoupons()
  const currencies = useCurrencies()
  const remove = useDeleteCoupon()
  const switching = useSwitchCoupon()
  const [deleting, setDeleting] = useState<Coupon>()
  const [notice, setNotice] = useState<string>()
  const location = useLocation()
  const navigate = useNavigate()
  // Read once, then dropped from history, so a reload or Back doesn't say it again.
  const [saved] = useState((location.state as SavedState | null)?.saved)
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  const confirmDelete = () => {
    if (!deleting) return
    remove.mutate(deleting.code, {
      onSuccess: () => setNotice(`Deleted ${deleting.code}.`),
      onSettled: () => setDeleting(undefined),
    })
  }
  const switchCoupon = (coupon: Coupon) => {
    remove.reset()
    setNotice(undefined)
    switching.mutate(
      { coupon, active: !coupon.active },
      { onSuccess: () => setNotice(`Switched ${coupon.code} ${coupon.active ? 'off' : 'on'}.`) },
    )
  }

  const shown = notice ?? (saved ? `Saved ${saved}.` : undefined)
  const error = coupons.error ?? currencies.error
  const ready = coupons.data && currencies.data

  return (
    <>
      <div className="page-heading">
        <h1>Promotions</h1>
        <ButtonLink to="/promotions/coupons/new" variant="primary">
          <Icon name="plus" size={16} />
          New Coupon
        </ButtonLink>
      </div>
      <PromotionsNav />
      <p className="muted page-intro">
        A Coupon is a code a Customer enters at checkout for a Discount. One Checkout Session takes one Coupon.
      </p>

      <div role="status">
        {shown && (
          <div className="alert alert-success">
            <Icon name="check" />
            <span>{shown}</span>
          </div>
        )}
      </div>
      {remove.error && <ErrorMessage error={remove.error} title={`Couldn't delete ${remove.variables}`} />}
      {switching.error && (
        <ErrorMessage error={switching.error} title={`Couldn't switch ${switching.variables?.coupon.code}`} />
      )}

      {error ? (
        <ErrorMessage
          error={error}
          title={coupons.error ? "Couldn't load the Coupons" : "Couldn't load the currencies"}
          retrying={coupons.isFetching || currencies.isFetching}
          onRetry={() => (coupons.error ? coupons.refetch() : currencies.refetch())}
        />
      ) : (
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Code</th>
                <th scope="col">Discount</th>
                <th scope="col">Minimum</th>
                <th scope="col">Valid</th>
                <th scope="col">Status</th>
                <th scope="col">
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            {ready ? (
              <tbody>
                {coupons.data.length === 0 && (
                  <tr>
                    <td colSpan={columns} className="table-empty">
                      No Coupons yet. <Link to="/promotions/coupons/new">Create the first one</Link>.
                    </td>
                  </tr>
                )}
                {coupons.data.map((coupon) => (
                  <tr key={coupon.code}>
                    <th scope="row">
                      <Link to={`/promotions/coupons/${encodeURIComponent(coupon.code)}`}>
                        <code>{coupon.code}</code>
                      </Link>
                    </th>
                    <td>{describeDiscount(coupon.discount, currencies.data)}</td>
                    <td>
                      {coupon.minimumSubtotal ? (
                        formatMoney(coupon.minimumSubtotal, currencies.data)
                      ) : (
                        <span className="muted">None</span>
                      )}
                    </td>
                    <td>{describeWindow(coupon)}</td>
                    <td>{coupon.active ? <Badge tone="success">On</Badge> : <Badge tone="warning">Off</Badge>}</td>
                    <td className="actions">
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={switching.isPending && switching.variables?.coupon.code === coupon.code}
                        onClick={() => switchCoupon(coupon)}
                      >
                        {coupon.active ? 'Switch off' : 'Switch on'}
                      </Button>
                      <ButtonLink
                        to={`/promotions/coupons/${encodeURIComponent(coupon.code)}`}
                        variant="ghost"
                        size="sm"
                        icon
                        aria-label={`Edit ${coupon.code}`}
                      >
                        <Icon name="edit" size={16} />
                      </ButtonLink>
                      <Button
                        variant="ghost"
                        size="sm"
                        icon
                        className="danger-text"
                        aria-label={`Delete ${coupon.code}`}
                        onClick={() => {
                          remove.reset()
                          switching.reset()
                          setNotice(undefined)
                          setDeleting(coupon)
                        }}
                      >
                        <Icon name="trash" size={16} />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            ) : (
              <SkeletonRows rows={3} columns={columns} />
            )}
          </table>
        </div>
      )}

      <ConfirmDialog
        open={deleting !== undefined}
        title={`Delete ${deleting?.code ?? 'Coupon'}?`}
        confirmLabel="Delete Coupon"
        confirming={remove.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setDeleting(undefined)}
      >
        Customers can no longer apply it, though Orders that used it keep its code. To stop it for now, switch it off
        instead. This can't be undone.
      </ConfirmDialog>
    </>
  )
}
