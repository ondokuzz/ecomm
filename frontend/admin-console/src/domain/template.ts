/** A Message template on one Notification channel, rendered with its Message type's sample content. */
export interface TemplatePreview {
  messageType: string
  channel: string
  /** The locale it was rendered in, such as `en`. */
  locale: string
  /** An email's subject, or a push's title; absent for an SMS. */
  subject?: string | null
  /** An email's HTML body, a whole document; absent for SMS and push. */
  html?: string | null
  text?: string | null
}

/** Initialisms a Message type keeps in capitals. */
const initialisms = new Set(['RMA', 'SMS'])

/** A Message type as Staff read it: `ORDER_CONFIRMED` is "Order confirmed". */
export function messageTypeLabel(type: string): string {
  return type
    .split('_')
    .map((word, i) => {
      if (initialisms.has(word)) return word
      const lower = word.toLowerCase()
      return i === 0 ? lower.charAt(0).toUpperCase() + lower.slice(1) : lower
    })
    .join(' ')
}

const channelLabels: Record<string, string> = { EMAIL: 'Email', SMS: 'SMS', PUSH: 'Push' }

/** A Notification channel's name. */
export function channelLabel(channel: string): string {
  return channelLabels[channel] ?? channel
}

/** What a message's `subject` is on its channel: an email's subject, a push's title. */
export function subjectLabel(channel: string): string {
  return channel === 'PUSH' ? 'Title' : 'Subject'
}
