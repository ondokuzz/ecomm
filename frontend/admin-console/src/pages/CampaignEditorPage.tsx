import { type FormEvent, useId, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useCampaign, useSaveCampaign } from '../api/campaigns'
import { useCategories } from '../api/categories'
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
import { type Campaign, type CampaignForm, campaignRequest, formOf } from '../domain/campaign'
import type { Category } from '../domain/category'
import type { Currencies } from '../domain/money'
import type { SavedState } from './CategoryListPage'

/** `/promotions/campaigns/new` creates a Campaign; `/promotions/campaigns/:id` edits one. */
export function CampaignEditorPage() {
  const { id } = useParams()
  const campaign = useCampaign(id)
  const currencies = useCurrencies()
  const categories = useCategories()

  if (isNotFound(campaign.error)) return <NotFound what="Campaign" />
  const failed = campaign.error
    ? { error: campaign.error, title: "Couldn't load the Campaign", query: campaign }
    : currencies.error
      ? { error: currencies.error, title: "Couldn't load the currencies", query: currencies }
      : categories.error
        ? { error: categories.error, title: "Couldn't load the Categories", query: categories }
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
  if (!currencies.data || !categories.data || (id !== undefined && !campaign.data)) {
    return (
      <div aria-busy="true">
        <BackLink />
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  return (
    <CampaignEditor
      key={id ?? 'new'}
      campaign={campaign.data}
      currencies={currencies.data}
      categories={categories.data}
    />
  )
}

function BackLink() {
  return (
    <Link to="/promotions/campaigns" className="back-link">
      <Icon name="arrowLeft" size={16} />
      Campaigns
    </Link>
  )
}

/**
 * The Campaign form: its name and priority, the Categories it is limited to, chosen from Catalog's,
 * and its discount, minimum and validity. Fields that can't be sent are marked before anything is;
 * Promotions checks the rest, and each field it refuses shows its message beside that field until
 * it is edited.
 */
function CampaignEditor({
  campaign,
  currencies,
  categories,
}: {
  campaign?: Campaign
  currencies: Currencies
  categories: Category[]
}) {
  const navigate = useNavigate()
  const save = useSaveCampaign(campaign?.id)
  const [form, setForm] = useState<CampaignForm>(() => formOf(campaign, currencies, new Date()))
  const fieldErrors = useFieldErrors()
  const { errors, setErrors, errorId, field, clearError } = fieldErrors
  const id = useId()

  // A Category the Campaign has that Catalog no longer does stays listed, so it can be unticked.
  const choices = [
    ...categories.map((category) => ({ slug: category.slug, name: category.name, known: true })),
    ...form.categories
      .filter((slug) => !categories.some((category) => category.slug === slug))
      .map((slug) => ({ slug, name: slug, known: false })),
  ]
  // Promotions names a Category by its place in the list sent, which is the form's.
  const categoryErrors = Object.entries(errors)
    .filter(([name]) => name === 'categories' || name.startsWith('categories['))
    .map(([name, message]) => {
      const index = /^categories\[(\d+)]$/.exec(name)?.[1]
      const slug = index === undefined ? undefined : form.categories[Number(index)]
      return slug ? `${slug}: ${message}` : message
    })
  const toggleCategory = (slug: string, checked: boolean) => {
    setForm((current) => ({
      ...current,
      categories: checked ? [...current.categories, slug] : current.categories.filter((c) => c !== slug),
    }))
    // The list renumbers, so the errors no longer name the right Category.
    setErrors((current) =>
      Object.fromEntries(Object.entries(current).filter(([name]) => !name.startsWith('categories'))),
    )
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    save.reset()
    const { request, errors: local } = campaignRequest(form, currencies)
    setErrors(local)
    if (!request) return
    save.mutate(request, {
      onSuccess: () => navigate('/promotions/campaigns', { state: { saved: request.name } satisfies SavedState }),
      onError: (error) => setErrors(fieldErrorsOf(error)),
    })
  }

  const hasErrors = Object.keys(errors).length > 0
  return (
    <form onSubmit={submit} noValidate className="editor">
      <BackLink />
      <div className="page-heading">
        <h1>{campaign ? campaign.name : 'New Campaign'}</h1>
      </div>

      {save.error ? (
        <ErrorMessage
          error={save.error}
          title="Couldn't save the Campaign"
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
          <label className="field">
            Name
            <Input
              value={form.name}
              onChange={(e) => {
                setForm({ ...form, name: e.target.value })
                clearError('name')
              }}
              autoFocus={!campaign}
              {...field('name')}
            />
            <FieldError id={errorId('name')} message={errors.name} />
          </label>
          <label className="field">
            Priority
            <Input
              inputMode="numeric"
              value={form.priority}
              onChange={(e) => {
                setForm({ ...form, priority: e.target.value })
                clearError('priority')
              }}
              {...field('priority')}
            />
            <span className="hint">A whole number no other Campaign has. Campaigns apply lowest first.</span>
            <FieldError id={errorId('priority')} message={errors.priority} />
          </label>
        </div>
      </section>

      <section className="panel" aria-labelledby={`${id}-categories`}>
        <h2 id={`${id}-categories`}>Categories</h2>
        <fieldset
          className="check-list"
          aria-describedby={categoryErrors.length > 0 ? errorId('categories') : undefined}
        >
          <legend className="visually-hidden">Categories the Campaign is limited to</legend>
          {choices.map((choice) => (
            <label key={choice.slug} className="check">
              <input
                type="checkbox"
                checked={form.categories.includes(choice.slug)}
                onChange={(e) => toggleCategory(choice.slug, e.target.checked)}
              />
              {choice.name}
              {!choice.known && <span className="muted"> (not in Catalog)</span>}
            </label>
          ))}
        </fieldset>
        <span className="hint">
          {form.categories.length === 0
            ? 'None ticked: the Campaign applies to every line.'
            : 'The Campaign applies only to lines in the ticked Categories.'}
        </span>
        <FieldError
          id={errorId('categories')}
          message={categoryErrors.length > 0 ? categoryErrors.join('; ') : undefined}
        />
      </section>

      <TermsFields
        form={form}
        onChange={(change) => setForm((current) => ({ ...current, ...change }))}
        currencies={currencies}
        errors={fieldErrors}
      />

      <div className="form-actions">
        <Button type="submit" variant="primary" loading={save.isPending}>
          {campaign ? 'Save changes' : 'Create Campaign'}
        </Button>
        <ButtonLink to="/promotions/campaigns" variant="ghost">
          Cancel
        </ButtonLink>
      </div>
    </form>
  )
}
