import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useCampaigns, useDeleteCampaign, useSwitchCampaign } from '../api/campaigns'
import { useCategories } from '../api/categories'
import { useCurrencies } from '../api/currencies'
import { PromotionsNav } from '../components/PromotionsNav'
import { ErrorMessage, SkeletonRows } from '../components/Status'
import { Badge, type Tone } from '../components/ui/Badge'
import { Button, ButtonLink } from '../components/ui/Button'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { Icon } from '../components/ui/Icon'
import type { Campaign, CampaignState } from '../domain/campaign'
import { formatMoney } from '../domain/money'
import { describeDiscount, describeWindow } from '../domain/promotion'
import type { SavedState } from './CategoryListPage'

const columns = 8

const states: Record<CampaignState, { label: string; tone: Tone }> = {
  running: { label: 'Running', tone: 'success' },
  scheduled: { label: 'Scheduled', tone: 'info' },
  over: { label: 'Over', tone: 'neutral' },
  off: { label: 'Off', tone: 'warning' },
}

export function CampaignListPage() {
  const campaigns = useCampaigns()
  const currencies = useCurrencies()
  const categories = useCategories()
  const remove = useDeleteCampaign()
  const switching = useSwitchCampaign()
  const [deleting, setDeleting] = useState<Campaign>()
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
    remove.mutate(deleting.id, {
      onSuccess: () => setNotice(`Deleted ${deleting.name}.`),
      onSettled: () => setDeleting(undefined),
    })
  }
  const switchCampaign = (campaign: Campaign) => {
    remove.reset()
    setNotice(undefined)
    switching.mutate(
      { campaign, active: !campaign.active },
      { onSuccess: () => setNotice(`Switched ${campaign.name} ${campaign.active ? 'off' : 'on'}.`) },
    )
  }
  // A Category Catalog no longer has is shown by its slug.
  const categoryName = (slug: string) => categories.data?.find((c) => c.slug === slug)?.name ?? slug

  const shown = notice ?? (saved ? `Saved ${saved}.` : undefined)
  const error = campaigns.error ?? currencies.error
  const ready = campaigns.data && currencies.data

  return (
    <>
      <div className="page-heading">
        <h1>Promotions</h1>
        <ButtonLink to="/promotions/campaigns/new" variant="primary">
          <Icon name="plus" size={16} />
          New Campaign
        </ButtonLink>
      </div>
      <PromotionsNav />
      <p className="muted page-intro">
        A Campaign is a Discount that applies itself to every qualifying Checkout Session, with no code. Campaigns apply
        in order of priority, lowest first.
      </p>

      <div role="status">
        {shown && (
          <div className="alert alert-success">
            <Icon name="check" />
            <span>{shown}</span>
          </div>
        )}
      </div>
      {remove.error && (
        <ErrorMessage
          error={remove.error}
          title={`Couldn't delete ${campaigns.data?.find((c) => c.id === remove.variables)?.name ?? 'the Campaign'}`}
        />
      )}
      {switching.error && (
        <ErrorMessage error={switching.error} title={`Couldn't switch ${switching.variables?.campaign.name}`} />
      )}

      {error ? (
        <ErrorMessage
          error={error}
          title={campaigns.error ? "Couldn't load the Campaigns" : "Couldn't load the currencies"}
          retrying={campaigns.isFetching || currencies.isFetching}
          onRetry={() => (campaigns.error ? campaigns.refetch() : currencies.refetch())}
        />
      ) : (
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th scope="col" className="num">
                  Priority
                </th>
                <th scope="col">Name</th>
                <th scope="col">Discount</th>
                <th scope="col">Categories</th>
                <th scope="col">Minimum</th>
                <th scope="col">Valid</th>
                <th scope="col">State</th>
                <th scope="col">
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            {ready ? (
              <tbody>
                {campaigns.data.length === 0 && (
                  <tr>
                    <td colSpan={columns} className="table-empty">
                      No Campaigns yet. <Link to="/promotions/campaigns/new">Create the first one</Link>.
                    </td>
                  </tr>
                )}
                {campaigns.data.map((campaign) => (
                  <tr key={campaign.id}>
                    <td className="num">{campaign.priority}</td>
                    <th scope="row">
                      <Link to={`/promotions/campaigns/${campaign.id}`}>{campaign.name}</Link>
                    </th>
                    <td>{describeDiscount(campaign.discount, currencies.data)}</td>
                    <td>
                      {campaign.categories.length === 0 ? (
                        <span className="muted">Everything</span>
                      ) : (
                        <ul className="tag-list">
                          {campaign.categories.map((slug) => (
                            <li key={slug}>
                              <Badge>{categoryName(slug)}</Badge>
                            </li>
                          ))}
                        </ul>
                      )}
                    </td>
                    <td>
                      {campaign.minimumSubtotal ? (
                        formatMoney(campaign.minimumSubtotal, currencies.data)
                      ) : (
                        <span className="muted">None</span>
                      )}
                    </td>
                    <td>{describeWindow(campaign)}</td>
                    <td>
                      <Badge tone={states[campaign.state].tone} dot>
                        {states[campaign.state].label}
                      </Badge>
                    </td>
                    <td className="actions">
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={switching.isPending && switching.variables?.campaign.id === campaign.id}
                        onClick={() => switchCampaign(campaign)}
                      >
                        {campaign.active ? 'Switch off' : 'Switch on'}
                      </Button>
                      <ButtonLink
                        to={`/promotions/campaigns/${campaign.id}`}
                        variant="ghost"
                        size="sm"
                        icon
                        aria-label={`Edit ${campaign.name}`}
                      >
                        <Icon name="edit" size={16} />
                      </ButtonLink>
                      <Button
                        variant="ghost"
                        size="sm"
                        icon
                        className="danger-text"
                        aria-label={`Delete ${campaign.name}`}
                        onClick={() => {
                          remove.reset()
                          switching.reset()
                          setNotice(undefined)
                          setDeleting(campaign)
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
        title={`Delete ${deleting?.name ?? 'Campaign'}?`}
        confirmLabel="Delete Campaign"
        confirming={remove.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setDeleting(undefined)}
      >
        It stops applying at checkout. To stop it for now, switch it off instead. This can't be undone.
      </ConfirmDialog>
    </>
  )
}
