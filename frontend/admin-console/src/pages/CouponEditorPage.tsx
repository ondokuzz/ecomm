import { type FormEvent, useId, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useCoupon, useSaveCoupon } from '../api/coupons'
import { useCurrencies } from '../api/currencies'
import { isNotFound } from '../api/failure'
import { fieldErrorsOf } from '../api/fieldErrors'
import { NotFound } from '../components/NotFound'
import { ErrorMessage, FieldError } from '../components/Status'
import { TermsFields } from '../components/TermsFields'
import { Button, ButtonLink } from '../components/ui/Button'
import { Icon } from '../components/ui/Icon'
import { Input } from '../components/ui/Input'
import { useFieldErrors } from '../components/useFieldErrors'
import { type Coupon, type CouponForm, couponRequest, formOf } from '../domain/coupon'
import type { Currencies } from '../domain/money'
import type { SavedState } from './CategoryListPage'

/** `/promotions/coupons/new` creates a Coupon; `/promotions/coupons/:code` edits one. */
export function CouponEditorPage() {
  const { code } = useParams()
  const coupon = useCoupon(code)
  const currencies = useCurrencies()

  if (isNotFound(coupon.error)) return <NotFound what="Coupon" />
  const failed = coupon.error
    ? { error: coupon.error, title: "Couldn't load the Coupon", query: coupon }
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
  if (!currencies.data || (code !== undefined && !coupon.data)) {
    return (
      <div aria-busy="true">
        <BackLink />
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  return <CouponEditor key={code ?? 'new'} coupon={coupon.data} currencies={currencies.data} />
}

function BackLink() {
  return (
    <Link to="/promotions/coupons" className="back-link">
      <Icon name="arrowLeft" size={16} />
      Coupons
    </Link>
  )
}

/**
 * The Coupon form: its code when it is new, its discount, minimum and validity. Fields that can't
 * be sent are marked before anything is; Promotions checks the rest, and each field it refuses shows
 * its message beside that field until it is edited.
 */
function CouponEditor({ coupon, currencies }: { coupon?: Coupon; currencies: Currencies }) {
  const navigate = useNavigate()
  const save = useSaveCoupon(coupon?.code)
  const [form, setForm] = useState<CouponForm>(() => formOf(coupon, currencies, new Date()))
  const fieldErrors = useFieldErrors()
  const { errors, setErrors, errorId, field, clearError } = fieldErrors
  const id = useId()

  const submit = (event: FormEvent) => {
    event.preventDefault()
    save.reset()
    const { request, errors: local } = couponRequest(form, currencies)
    setErrors(local)
    if (!request) return
    save.mutate(request, {
      onSuccess: (saved) =>
        navigate('/promotions/coupons', { state: { saved: saved?.code ?? request.code } satisfies SavedState }),
      onError: (error) => setErrors(fieldErrorsOf(error)),
    })
  }

  const hasErrors = Object.keys(errors).length > 0
  return (
    <form onSubmit={submit} noValidate className="editor">
      <BackLink />
      <div className="page-heading">
        <h1>{coupon ? <code>{coupon.code}</code> : 'New Coupon'}</h1>
      </div>

      {save.error ? (
        <ErrorMessage
          error={save.error}
          title="Couldn't save the Coupon"
          // Each field's own message is beside it; repeating it here would say it twice.
          message={hasErrors ? 'Fix the fields marked below, then save again.' : undefined}
        />
      ) : (
        hasErrors && (
          <div className="alert alert-danger" role="alert">
            <Icon name="alert" />
            <span>Fix the fields marked below, then save again.</span>
          </div>
        )
      )}

      <section className="panel" aria-labelledby={`${id}-details`}>
        <h2 id={`${id}-details`}>Details</h2>
        <div className="form-grid">
          {coupon ? (
            <div className="field">
              Code
              <code className="readonly">{coupon.code}</code>
              <span className="hint">A code never changes; Orders record the code of the Coupon they used.</span>
            </div>
          ) : (
            <label className="field">
              Code
              <Input
                value={form.code}
                onChange={(e) => {
                  setForm({ ...form, code: e.target.value })
                  clearError('code')
                }}
                autoFocus
                spellCheck={false}
                autoCapitalize="characters"
                {...field('code')}
              />
              <span className="hint">
                Up to 64 letters, digits, hyphens or underscores, such as SPRING-25. Customers may type it in any case.
              </span>
              <FieldError id={errorId('code')} message={errors.code} />
            </label>
          )}
        </div>
      </section>

      <TermsFields
        form={form}
        onChange={(change) => setForm((current) => ({ ...current, ...change }))}
        currencies={currencies}
        errors={fieldErrors}
      />

      <div className="form-actions">
        <Button type="submit" variant="primary" loading={save.isPending}>
          {coupon ? 'Save changes' : 'Create Coupon'}
        </Button>
        <ButtonLink to="/promotions/coupons" variant="ghost">
          Cancel
        </ButtonLink>
      </div>
    </form>
  )
}
