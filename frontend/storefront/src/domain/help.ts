/**
 * Help: the Customer's Conversation with the Support Assistant. It answers about their own Orders
 * and RMAs, reading them with the Customer's own access, and about the Catalog's Products, and never
 * acts.
 */

/** A record a reply names, for the Storefront to link to its page. */
export interface Reference {
  kind: 'ORDER' | 'RMA' | 'PRODUCT'
  /** Its ID, which its page is found by; a Product's SKU. */
  id: string
  /** How the Customer knows it, such as the Order reference `#EA2CA751`, the RMA reference `RMA-3F2A9C1B` or a SKU. */
  reference: string
  label: string
}

export interface HelpMessage {
  role: 'CUSTOMER' | 'ASSISTANT'
  text: string
  /** The records an assistant's reply names; a Customer's message has none. */
  references: Reference[]
  at: string
}

/** A Conversation's transcript, oldest first. */
export interface Conversation {
  id: string
  messages: HelpMessage[]
}

/** How long a message may be, in characters, as the Support Assistant takes it. */
export const messageLimit = 2000

/**
 * When a Customer past their message limits may ask again: `retryAfter` seconds from `now`, rounded
 * up to the minute, as the panel shows it.
 */
export function askAgainAt(retryAfter: number, now: Date): Date {
  const minute = 60_000
  return new Date(Math.ceil((now.getTime() + retryAfter * 1000) / minute) * minute)
}

export function remainingCharacters(text: string): number {
  return messageLimit - text.length
}

/** Where a reference's link goes: an Order to its Order page, an RMA to its RMA page, a Product to its Product page. */
export function referencePath(reference: Reference): string {
  switch (reference.kind) {
    case 'ORDER':
      return `/orders/${encodeURIComponent(reference.id)}`
    case 'RMA':
      return `/returns/${encodeURIComponent(reference.id)}`
    case 'PRODUCT':
      return `/products/${encodeURIComponent(reference.id)}`
  }
}
