/**
 * Help: the Customer's Conversation with the Support Assistant. It answers about their own Orders,
 * reading them with the Customer's own access, and never acts.
 */

/** A record a reply names, for the Storefront to link to its page. */
export interface Reference {
  kind: 'ORDER'
  id: string
  /** How the Customer knows it, such as the Order reference `#EA2CA751`. */
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

export function remainingCharacters(text: string): number {
  return messageLimit - text.length
}

/** Where a reference's link goes: an Order to its Order page. */
export function referencePath(reference: Reference): string {
  switch (reference.kind) {
    case 'ORDER':
      return `/orders/${encodeURIComponent(reference.id)}`
  }
}
