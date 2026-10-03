import type { FieldErrors } from '../api/fieldErrors'
import type { Currencies } from './money'
import { type Terms, type TermsForm, termsFormOf, termsRequest, wholeNumber } from './promotion'

/** Where a Campaign stands now: an active one is `scheduled`, then `running`, then `over`. */
export type CampaignState = 'running' | 'scheduled' | 'over' | 'off'

/**
 * A Discount Staff run for every qualifying Checkout Session, with no code, as Promotions answers
 * it. It is limited to the lines of its `categories`, or applies to every line when there are
 * none. Campaigns apply by `priority`, lowest first; no two have the same.
 */
export interface Campaign extends Terms {
  id: string
  name: string
  /** Catalog Category slugs. */
  categories: string[]
  priority: number
  state: CampaignState
}

/** A Campaign as Staff send it; Promotions gives it its ID and works out its state. */
export type CampaignRequest = Omit<Campaign, 'id' | 'state'>

/** The Campaign editor's state. The Categories are in the order they are sent, so `categories[i]` names the same one on the server. */
export interface CampaignForm extends TermsForm {
  name: string
  categories: string[]
  priority: string
}

/** `campaign` laid out for editing, or a new Campaign that applies to everything, starting `now`. */
export function formOf(campaign: Campaign | undefined, currencies: Currencies, now: Date): CampaignForm {
  return {
    name: campaign?.name ?? '',
    ...termsFormOf(campaign, currencies, now),
    categories: [...(campaign?.categories ?? [])],
    priority: campaign?.priority.toString() ?? '',
  }
}

/**
 * The form as Promotions takes it, or the fields it can't send, named as Promotions would name
 * them: those of its terms, and a `priority` that isn't a whole number.
 */
export function campaignRequest(
  form: CampaignForm,
  currencies: Currencies,
): { request: CampaignRequest; errors: FieldErrors } | { request?: undefined; errors: FieldErrors } {
  const errors: FieldErrors = {}
  const terms = termsRequest(form, currencies, errors)
  const priority = wholeNumber(form.priority)
  if (priority === undefined) errors.priority = 'must be a whole number, 0 or more'
  if (Object.keys(errors).length > 0) return { errors }
  return {
    request: { name: form.name.trim(), ...terms, categories: form.categories, priority: priority! },
    errors,
  }
}

/** `campaign` as Staff send it, switched on or off and otherwise as it is. */
export function switched(campaign: Campaign, active: boolean): CampaignRequest {
  const { id: _, state: __, ...request } = campaign
  return { ...request, active }
}
