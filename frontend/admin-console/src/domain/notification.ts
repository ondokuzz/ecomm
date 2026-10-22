/** One message on one Notification channel to one Customer, as Notifications' ledger keeps it. */
export interface Notification {
  id: string
  messageType: string
  channel: string
  customerId: string
  orderId: string | null
  rmaId: string | null
  /** Where it went, masked: `a***@example.com`, `+49***78`, or for push the Customer's ID. */
  recipient: string | null
  status: NotificationStatus
  /** As last rendered; null until it was. `subject` is an email's subject or a push's title. */
  message: { subject: string | null; html: string | null; text: string | null } | null
  attempts: { number: number; at: string; outcome: string; reason: string | null }[]
  createdAt: string
  sentAt: string | null
  nextAttemptAt: string | null
}

export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED'
